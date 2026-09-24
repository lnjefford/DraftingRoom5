package dev.draftingroom5.retirement

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class RetirementBackupBoundaryTest {
    @Test fun ordinaryBackupAndDeviceTransferUseSanitizedGlobalSnapshots() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val file = projectFile("app/src/main/res/xml/$name")
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("include")
            val includes = List(nodes.length) { nodes.item(it) as Element }.map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()
            assertEquals(setOf("file" to "current-backups/latest.json", "file" to "current-backups/previous.json", "sharedpref" to "current-backup-status.xml"), includes)
        }
        val databaseSource = projectFile("app/src/main/java/dev/draftingroom5/retirement/data/RetirementDatabase.kt").readText()
        assertTrue(databaseSource.contains("context.noBackupFilesDir"))
        assertFalse(projectFile("app/src/main/java/dev/draftingroom5/AppDocumentCodec.kt").readText().contains("retirement", ignoreCase = true))
        val backupSource = projectFile("app/src/main/java/dev/draftingroom5/BackupSupport.kt").readText()
        assertTrue(backupSource.contains("RetirementCodec.encode"))
        assertTrue(backupSource.contains("RetirementCodec.decode"))
    }

    @Test fun persistedCodecCannotCarrySecretsOrRawShareworksContent() {
        val fields = RetirementCodecSurface.fields()
        listOf("secret", "accessToken", "apiKey", "workbookBytes", "sourceUri", "sourcePath", "sourceFilename").forEach { forbidden ->
            assertFalse(forbidden, fields.contains(forbidden, ignoreCase = true))
        }
    }

    @Test fun productionRetirementCodeHasNoAdHocLoggingOrPrivateWorkPayloads() {
        val root = projectFile("app/src/main/java/dev/draftingroom5/retirement")
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val combined = sources.joinToString("\n") { it.readText() }
        listOf("android.util.Log", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "println(", "printStackTrace(").forEach {
            assertFalse("Forbidden diagnostic call: $it", combined.contains(it))
        }
        val worker = sources.single { it.name == "RetirementAccountSyncWorker.kt" }.readText()
        listOf("access_token", "api_key", "secret", "address", "workbook").forEach {
            assertFalse("Private WorkManager payload: $it", worker.contains("workDataOf($it", ignoreCase = true))
        }
        assertTrue(worker.contains("workDataOf(PROPERTY to propertyId)"))
        assertTrue(worker.contains("workDataOf(ITEM to itemId)"))
        assertTrue(worker.contains("NetworkType.CONNECTED"))
        assertTrue(worker.contains("setRequiresBatteryNotLow(true)"))
        assertTrue(worker.contains("BackoffPolicy.EXPONENTIAL"))
        assertTrue(worker.contains("ExistingPeriodicWorkPolicy.UPDATE"))
    }

    private fun projectFile(path: String) = sequenceOf(File(path), File("../$path")).first { it.exists() }
}

private object RetirementCodecSurface {
    fun fields(): String = listOf(
        "RetirementDomain.kt", "../data/RetirementCodec.kt", "../data/RetirementDatabase.kt"
    ).map { relative -> File("app/src/main/java/dev/draftingroom5/retirement/domain", relative).normalize() }
        .map { direct -> if (direct.isFile) direct else File("../${direct.path}") }
        .joinToString("\n") { it.readText() }
}
