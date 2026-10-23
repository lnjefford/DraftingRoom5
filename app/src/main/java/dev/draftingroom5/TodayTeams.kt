package dev.draftingroom5

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

internal data class TodayNextGame(
    val team: TodayTeam,
    val opponent: String,
    val startsAt: Instant,
    val home: Boolean,
    val timeConfirmed: Boolean,
    val teamLogo: String?,
    val opponentLogo: String?,
)

internal data class TodayTeamSnapshot(val record: String?, val nextGame: TodayNextGame?)

private data class TeamEndpoint(val sport: String, val league: String, val id: String)

private fun TodayTeam.endpoint(): TeamEndpoint = when (this) {
    TodayTeam.BREWERS -> TeamEndpoint("baseball", "mlb", "mil")
    TodayTeam.PACKERS -> TeamEndpoint("football", "nfl", "gb")
    TodayTeam.INDIANA_FOOTBALL -> TeamEndpoint("football", "college-football", "84")
    TodayTeam.INDIANA_BASKETBALL -> TeamEndpoint("basketball", "mens-college-basketball", "84")
    TodayTeam.WISCONSIN_FOOTBALL -> TeamEndpoint("football", "college-football", "275")
    TodayTeam.WISCONSIN_BASKETBALL -> TeamEndpoint("basketball", "mens-college-basketball", "275")
}

internal fun parseTodayNextGame(team: TodayTeam, text: String, now: Instant): TodayNextGame? {
    val root = JSONObject(text)
    val own = root.getJSONObject("team")
    val ownId = own.getString("id")
    val events = root.optJSONArray("events") ?: return null
    return (0 until events.length()).mapNotNull { index ->
        val event = events.optJSONObject(index) ?: return@mapNotNull null
        val start = runCatching { OffsetDateTime.parse(event.getString("date")).toInstant() }
            .getOrNull() ?: return@mapNotNull null
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
        val state = competition.optJSONObject("status")?.optJSONObject("type")?.optString("state")
        if (state != "pre" || start < now) return@mapNotNull null
        val competitors = competition.optJSONArray("competitors") ?: return@mapNotNull null
        val sides = (0 until competitors.length()).mapNotNull { competitors.optJSONObject(it) }
        val self = sides.firstOrNull { it.optString("id") == ownId } ?: return@mapNotNull null
        val other = sides.firstOrNull { it.optString("id") != ownId } ?: return@mapNotNull null
        val otherTeam = other.optJSONObject("team") ?: return@mapNotNull null
        val opponent = otherTeam.optString("shortDisplayName").ifBlank { otherTeam.optString("displayName") }
            .takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TodayNextGame(team, opponent, start, self.optString("homeAway") == "home",
            event.optBoolean("timeValid", true), own.optString("logo").takeIf { it.isNotBlank() },
            otherTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() })
    }.minByOrNull { it.startsAt }
}

internal fun parseTodayTeamSnapshot(team: TodayTeam, text: String, now: Instant): TodayTeamSnapshot {
    val own = JSONObject(text).getJSONObject("team")
    val items = own.optJSONObject("record")?.optJSONArray("items")
    val overall = items?.let { records ->
        (0 until records.length()).mapNotNull { records.optJSONObject(it) }
            .firstOrNull { it.optString("type").equals("total", true) ||
                it.optString("description").equals("overall", true) }
    }
    val summary = own.optString("recordSummary").ifBlank { overall?.optString("summary").orEmpty() }.trim()
        .takeIf { it.matches(Regex("\\d+-\\d+(?:-\\d+)?")) }
    return TodayTeamSnapshot(summary, parseTodayNextGame(team, text, now))
}

internal object TodayTeamSource {
    suspend fun fetch(team: TodayTeam, today: LocalDate, now: Instant = Instant.now()): TodayTeamSnapshot =
        withContext(Dispatchers.IO) {
            val endpoint = team.endpoint()
            val season = if (endpoint.league == "mens-college-basketball" && today.monthValue >= 7)
                today.year + 1 else today.year
            var current: TodayTeamSnapshot? = null
            for (year in season..season + 1) {
                val address = "https://site.api.espn.com/apis/site/v2/sports/${endpoint.sport}/${endpoint.league}" +
                    "/teams/${endpoint.id}/schedule?season=$year"
                val snapshot = runCatching { parseTodayTeamSnapshot(team, readTeamText(address), now) }
                    .getOrElse { error -> current?.let { return@withContext it } ?: throw error }
                if (year == season) current = snapshot
                if (snapshot.nextGame != null) return@withContext TodayTeamSnapshot(
                    current?.record ?: snapshot.record, snapshot.nextGame)
            }
            current ?: TodayTeamSnapshot(null, null)
        }

    suspend fun logo(address: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = URL(address)
        if (url.protocol != "https" || url.host != "a.espncdn.com") return@withContext null
        val bytes = readTeamBytes(address, 300_000)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

private fun readTeamText(address: String): String = readTeamBytes(address, 1_000_000).toString(Charsets.UTF_8)

private fun readTeamBytes(address: String, limit: Int): ByteArray {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json,image/png")
    }
    try {
        if (connection.responseCode !in 200..299) error("Team schedule unavailable")
        return connection.inputStream.use { stream ->
            val bytes = stream.readNBytes(limit + 1)
            require(bytes.size <= limit) { "Team response too large" }
            bytes
        }
    } finally {
        connection.disconnect()
    }
}
