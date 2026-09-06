package dev.draftingroom5

import kotlinx.coroutines.CancellationException
import java.time.Instant

internal data class HealthReadResult<T>(val value: T? = null, val issue: String? = null)

/** A missing permission or a failed metric must not hide successfully read measurements. */
internal suspend fun <T> readHealthValue(
    permitted: Boolean,
    read: suspend () -> T?,
): HealthReadResult<T> {
    if (!permitted) return HealthReadResult(issue = "Read permission is off. Enable it in Health Connect settings.")
    return try {
        val value = read()
        HealthReadResult(value, if (value == null) "No readable records returned. Check the entry date and this app's read access in Health Connect." else null)
    } catch (error: CancellationException) {
        throw error
    } catch (_: SecurityException) {
        HealthReadResult(issue = "Health Connect denied access. Check this app's permissions and the entry date.")
    } catch (error: Exception) {
        HealthReadResult(issue = "Read failed (${error.javaClass.simpleName}). Tap Refresh to retry.")
    }
}

internal data class HealthRecordPage<T>(val records: List<T>, val nextToken: String?)

/** Descending queries can return an empty page with a continuation token. */
internal suspend fun <T> latestHealthRecord(
    timestamp: (T) -> Instant,
    query: suspend (String?) -> HealthRecordPage<T>,
): T? {
    var token: String? = null
    val visitedTokens = mutableSetOf<String>()
    do {
        val page = query(token)
        page.records.maxByOrNull(timestamp)?.let { return it }
        token = page.nextToken
        check(token == null || visitedTokens.add(token)) { "Health Connect repeated a page token." }
    } while (token != null)
    return null
}
