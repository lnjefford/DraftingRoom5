package dev.draftingroom5

import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.CancellationException
import java.time.Instant

internal enum class HealthReadOutcome { SUCCESS, MISSING, UNAVAILABLE }

internal data class HealthReadResult<T>(
    val value: T? = null,
    val issue: String? = null,
    val outcome: HealthReadOutcome = HealthReadOutcome.UNAVAILABLE,
)

/** A missing permission or a failed metric must not hide successfully read measurements. */
internal suspend fun <T> readHealthValue(
    permitted: Boolean,
    read: suspend () -> T?,
): HealthReadResult<T> {
    if (!permitted) return HealthReadResult(issue = "Read permission is off. Enable it in Health Connect settings.")
    return try {
        val value = read()
        if (value == null) {
            HealthReadResult(
                issue = "No readable records returned. Check the entry date and this app's read access in Health Connect.",
                outcome = HealthReadOutcome.MISSING,
            )
        } else {
            HealthReadResult(value = value, outcome = HealthReadOutcome.SUCCESS)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: SecurityException) {
        HealthReadResult(issue = "Health Connect denied access. Check this app's permissions and the entry date.")
    } catch (error: Exception) {
        HealthReadResult(issue = "Read failed (${error.javaClass.simpleName}). Tap Refresh to retry.")
    }
}

internal data class HealthRecordPage<T>(val records: List<T>, val nextToken: String?)

internal fun requestedHealthPermissions(base: Set<String>, historyAvailable: Boolean): Set<String> =
    if (historyAvailable) base + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY else base

/** Find the newest record across all pages without depending on provider sort order. */
internal suspend fun <T> latestHealthRecord(
    timestamp: (T) -> Instant,
    query: suspend (String?) -> HealthRecordPage<T>,
): T? {
    var token: String? = null
    var latest: T? = null
    val visitedTokens = mutableSetOf<String>()
    do {
        val page = query(token)
        page.records.maxByOrNull(timestamp)?.let { candidate ->
            val previous = latest
            if (previous == null || timestamp(candidate).isAfter(timestamp(previous))) latest = candidate
        }
        token = page.nextToken?.takeIf { it.isNotEmpty() }
        check(token == null || visitedTokens.add(token)) { "Health Connect repeated a page token." }
    } while (token != null)
    return latest
}
