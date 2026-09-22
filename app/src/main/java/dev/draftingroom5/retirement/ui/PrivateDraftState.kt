package dev.draftingroom5.retirement.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** The Android Bundle contains an opaque ID only. Drafts expire and never enter backup. */
internal class PrivateDraftStore(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    fun save(id: String, binding: String, values: List<String>): String {
        UUID.fromString(id)
        require(values.size <= 1000 && values.all { it.length <= 4096 })
        val bytes = JSONObject().put("expires", now() + 86_400_000L).put("binding", binding)
            .put("values", JSONArray(values)).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65536)
        check(directory.exists() || directory.mkdirs())
        directory.listFiles()?.filter { it.extension == "json" && it.lastModified() < now() - 86_400_000L }?.forEach { it.delete() }
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(127)?.forEach { it.delete() }
        val temp = File(directory, "$id.tmp")
        try {
            temp.outputStream().use { it.write(bytes); it.fd.sync() }
            Files.move(temp.toPath(), File(directory, "$id.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { bytes.fill(0); temp.delete() }
        return id
    }

    fun restore(id: String, binding: String): List<String>? = runCatching {
        UUID.fromString(id)
        val file = File(directory, "$id.json")
        require(file.length() in 1..65536)
        val root = JSONObject(file.readText())
        if (root.getLong("expires") <= now() || root.getString("binding") != binding) {
            file.delete(); return null
        }
        val array = root.getJSONArray("values")
        require(array.length() <= 1000)
        List(array.length()) { array.getString(it).also { value -> require(value.length <= 4096) } }
    }.getOrNull()
}

@Composable
internal fun <T : Any> privateDraftSaver(binding: String, delegate: Saver<T, out Any>): Saver<T, String> {
    val context = LocalContext.current
    val store = remember(context) { lazy { PrivateDraftStore(File(context.noBackupFilesDir, "retirement-drafts")) } }
    val id = remember(binding) { UUID.randomUUID().toString() }
    return remember(binding, delegate, store, id) {
        Saver(save = { value ->
            val saved = with(delegate) { save(value) } as? List<*>
            saved?.takeIf { it.all { field -> field is String } }?.let { fields ->
                runCatching { store.value.save(id, binding, fields.map { it as String }) }.getOrNull()
            }
        }, restore = { key ->
            store.value.restore(key, binding)?.let { fields ->
                @Suppress("UNCHECKED_CAST")
                runCatching { (delegate as Saver<T, Any>).restore(fields) }.getOrNull()
            }
        })
    }
}

@Composable
internal fun <T> rememberPrivateState(vararg inputs: Any?, init: () -> MutableState<T>): MutableState<T> {
    val initial = remember(*inputs) { init().value }
    val delegate = remember(initial) { Saver<MutableState<T>, List<String>>(
        save = { state -> listOf(when (val value = state.value) {
            null -> "null"
            is String -> "string:$value"
            is Boolean -> "boolean:$value"
            is Enum<*> -> "enum:${value.name}"
            else -> error("Unsupported private draft type")
        }) },
        restore = { fields ->
            val text = fields.single()
            val value: Any? = when {
                text == "null" -> null
                text.startsWith("string:") -> text.removePrefix("string:")
                text.startsWith("boolean:") -> text.removePrefix("boolean:").toBooleanStrict()
                text.startsWith("enum:") && initial is Enum<*> -> requireNotNull(initial.javaClass.enumConstants).single { (it as Enum<*>).name == text.removePrefix("enum:") }
                else -> error("Invalid private draft")
            }
            @Suppress("UNCHECKED_CAST")
            mutableStateOf(value as T)
        },
    ) }
    return rememberSaveable(*inputs, saver = privateDraftSaver(inputs.joinToString("|"), delegate), init = init)
}
