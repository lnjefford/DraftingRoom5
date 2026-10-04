package dev.draftingroom5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDateTime
import kotlin.coroutines.resume

internal enum class TodayWeatherKind { CLEAR, CLOUDY, RAIN, SNOW, STORM, UNKNOWN }
internal enum class TodayDaypart { DAWN, DAY, DUSK, NIGHT }
internal enum class TodaySeason { SPRING, SUMMER, AUTUMN, WINTER }

internal fun seasonForDate(date: java.time.LocalDate): TodaySeason = when (date.monthValue) {
    in 3..5 -> TodaySeason.SPRING
    in 6..8 -> TodaySeason.SUMMER
    in 9..11 -> TodaySeason.AUTUMN
    else -> TodaySeason.WINTER
}

internal data class TodayWeather(
    val location: String,
    val temperatureF: Int,
    val weatherCode: Int,
    val localHour: Int,
    val refreshedAtMillis: Long,
) {
    val kind: TodayWeatherKind get() = weatherKind(weatherCode)
    val daypart: TodayDaypart get() = daypartForHour(localHour)
}

internal data class TodayCity(val name: String, val latitude: Double, val longitude: Double)

internal fun weatherKind(code: Int): TodayWeatherKind = when (code) {
    0, 1 -> TodayWeatherKind.CLEAR
    2, 3, 45, 48 -> TodayWeatherKind.CLOUDY
    in 51..67, in 80..82 -> TodayWeatherKind.RAIN
    in 71..77, in 85..86 -> TodayWeatherKind.SNOW
    in 95..99 -> TodayWeatherKind.STORM
    else -> TodayWeatherKind.UNKNOWN
}

internal fun daypartForHour(hour: Int): TodayDaypart = when (hour) {
    in 5..8 -> TodayDaypart.DAWN
    in 9..16 -> TodayDaypart.DAY
    in 17..20 -> TodayDaypart.DUSK
    else -> TodayDaypart.NIGHT
}

internal fun todaySceneResource(kind: TodayWeatherKind, daypart: TodayDaypart): Int = when (kind) {
    TodayWeatherKind.CLOUDY -> when (daypart) {
        TodayDaypart.DAWN, TodayDaypart.DUSK -> R.drawable.today_cloudy
        TodayDaypart.DAY -> R.drawable.today_cloudy_day
        TodayDaypart.NIGHT -> R.drawable.today_cloudy_night
    }
    TodayWeatherKind.RAIN -> when (daypart) {
        TodayDaypart.DAWN, TodayDaypart.DAY -> R.drawable.today_rain_day
        TodayDaypart.DUSK -> R.drawable.today_rain
        TodayDaypart.NIGHT -> R.drawable.today_rain_night
    }
    TodayWeatherKind.SNOW -> when (daypart) {
        TodayDaypart.DAWN, TodayDaypart.DAY -> R.drawable.today_snow_day
        TodayDaypart.DUSK -> R.drawable.today_snow
        TodayDaypart.NIGHT -> R.drawable.today_snow_night
    }
    TodayWeatherKind.STORM -> R.drawable.today_storm
    TodayWeatherKind.CLEAR, TodayWeatherKind.UNKNOWN -> when (daypart) {
        TodayDaypart.DAWN -> R.drawable.today_dawn
        TodayDaypart.DAY -> R.drawable.today_day
        TodayDaypart.DUSK -> R.drawable.today_dusk
        TodayDaypart.NIGHT -> R.drawable.today_night
    }
}

/** User-selected city is saved; precise current coordinates are never persisted. */
internal class TodayLocationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("today-location", Context.MODE_PRIVATE)

    fun shouldPromptForLocation(): Boolean = !preferences.getBoolean("location-prompted", false)
    fun markLocationPrompted() { preferences.edit().putBoolean("location-prompted", true).apply() }

    fun selectedCity(): TodayCity? {
        val name = preferences.getString("city", null)?.takeIf { it.isNotBlank() } ?: return null
        val latitude = preferences.getString("latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = preferences.getString("longitude", null)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return TodayCity(name, latitude, longitude)
    }

    fun setSelectedCity(city: TodayCity?) {
        preferences.edit().apply {
            if (city == null) remove("city").remove("latitude").remove("longitude")
            else putString("city", city.name).putString("latitude", city.latitude.toString())
                .putString("longitude", city.longitude.toString())
        }.apply()
    }
}

internal object TodayWeatherSource {
    suspend fun currentLocation(context: Context): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (last != null && System.currentTimeMillis() - last.time < 2 * 60 * 60 * 1000L) return last
            val current = runCatching {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
            }.getOrNull()
            if (current != null) return current
        }
        return null
    }

    suspend fun findCity(query: String): TodayCity? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val root = readJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
        val result = root.optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
        val city = result.optString("name").takeIf { it.isNotBlank() } ?: return@withContext null
        val region = result.optString("admin1").takeIf { it.isNotBlank() }
        TodayCity(listOfNotNull(city, region).joinToString(", "), result.getDouble("latitude"), result.getDouble("longitude"))
    }

    suspend fun fetch(latitude: Double, longitude: Double, label: String): TodayWeather = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val root = readJson("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit&timezone=auto")
        val current = root.getJSONObject("current")
        val hour = LocalDateTime.parse(current.getString("time")).hour
        TodayWeather(label, current.getDouble("temperature_2m").toInt(), current.getInt("weather_code"), hour,
            System.currentTimeMillis())
    }

    private fun readJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("Weather service unavailable")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            require(text.length <= 256_000) { "Weather response too large" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
