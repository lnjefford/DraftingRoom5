package dev.draftingroom5

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

internal data class TodayMarketQuote(
    val symbol: String,
    val price: Double,
    val previousClose: Double?,
    val currency: String,
    val updatedAtSeconds: Long,
    val points: List<Double>,
) {
    val changePercent: Double? get() = previousClose?.takeIf { it > 0.0 }?.let { (price - it) / it * 100.0 }
}

internal fun parseTodayMarketQuote(symbol: String, text: String): TodayMarketQuote {
    val result = JSONObject(text).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
    val meta = result.getJSONObject("meta")
    require(meta.getString("symbol").equals(symbol, ignoreCase = true)) { "Unexpected market symbol" }
    val price = meta.optDouble("regularMarketPrice", Double.NaN)
    require(price.isFinite() && price > 0.0) { "Market price unavailable" }
    val previous = meta.optDouble("previousClose", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
    val updated = meta.optLong("regularMarketTime", 0L)
    require(updated > 0L) { "Market timestamp unavailable" }
    val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
    val points = (0 until closes.length()).mapNotNull { index ->
        closes.optDouble(index, Double.NaN).takeIf { it.isFinite() && it > 0.0 }
    }.takeLast(45)
    return TodayMarketQuote(symbol, price, previous, meta.optString("currency"), updated, points)
}

internal object TodayMarketSource {
    suspend fun fetch(symbol: String): TodayMarketQuote = withContext(Dispatchers.IO) {
        require(symbol.matches(Regex("\\^?[A-Z0-9][A-Z0-9.-]{0,11}")))
        val encoded = URLEncoder.encode(symbol, "UTF-8")
        val response = readBytes("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=5d&interval=1h", 512_000)
        parseTodayMarketQuote(symbol, response.toString(Charsets.UTF_8))
    }
}

internal object TodayMarketLogoSource {
    suspend fun fetch(symbol: String): Bitmap? = withContext(Dispatchers.IO) {
        if (symbol.startsWith("^")) return@withContext null
        require(symbol.matches(Regex("[A-Z0-9][A-Z0-9.-]{0,11}")))
        val encoded = URLEncoder.encode(symbol, "UTF-8")
        val search = JSONObject(readBytes("https://www.allinvestview.com/api/logo-search/?q=$encoded", 64_000)
            .toString(Charsets.UTF_8)).optJSONArray("results") ?: return@withContext null
        val match = (0 until search.length()).mapNotNull { search.optJSONObject(it) }
            .firstOrNull { it.optString("symbol").equals(symbol, ignoreCase = true) } ?: return@withContext null
        val website = match.optString("website")
        val host = runCatching { URL(if (website.contains("://")) website else "https://$website").host }
            .getOrNull()?.removePrefix("www.") ?: return@withContext null
        if (!host.matches(Regex("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+"))) return@withContext null
        val image = readBytes("https://cdn.tickerlogos.com/$host", 256_000)
        BitmapFactory.decodeByteArray(image, 0, image.size)
    }
}

private fun readBytes(address: String, limit: Int): ByteArray {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "DraftingRoom5/1.0")
    }
    try {
        if (connection.responseCode !in 200..299) error("Market service unavailable")
        return connection.inputStream.use { stream ->
            val bytes = stream.readNBytes(limit + 1)
            require(bytes.size <= limit) { "Market response too large" }
            bytes
        }
    } finally {
        connection.disconnect()
    }
}
