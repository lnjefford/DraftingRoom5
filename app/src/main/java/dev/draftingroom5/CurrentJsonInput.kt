package dev.draftingroom5

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

internal fun readBoundedCurrentJson(stream: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
        output.write(buffer, 0, count)
    }
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("Document is not valid UTF-8.", error)
    }
}
