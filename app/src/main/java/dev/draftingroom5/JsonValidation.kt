package dev.draftingroom5

import org.json.JSONTokener

// Android's JSONTokener accepts non-JSON syntax and silently replaces duplicate keys.
// Validate the grammar before handing a bounded document to that platform parser.
internal fun inspectJsonStructure(value: String) {
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
    StrictJsonReader(value).read()
}

private class StrictJsonReader(private val text: String) {
    private var position = 0

    fun read() {
        value(0)
        whitespace()
        require(position == text.length) { "Trailing JSON content." }
    }

    private fun whitespace() {
        while (position < text.length && text[position] in " \t\r\n") position++
    }

    private fun take(character: Char): Boolean {
        whitespace()
        if (position == text.length || text[position] != character) return false
        position++
        return true
    }

    private fun value(depth: Int) {
        whitespace()
        require(position < text.length) { "Missing JSON value." }
        when (text[position]) {
            '{', '[' -> {
                require(depth < 32) { "JSON nesting exceeds 32." }
                val objectValue = text[position++] == '{'
                val close = if (objectValue) '}' else ']'
                val keys = hashSetOf<String>()
                if (take(close)) return
                do {
                    if (objectValue) {
                        whitespace()
                        require(keys.add(string())) { "Duplicate object key." }
                        require(take(':')) { "Expected colon." }
                    }
                    value(depth + 1)
                    if (take(close)) return
                } while (take(','))
                throw IllegalArgumentException("Expected closing delimiter.")
            }
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> {
                val match = NUMBER.find(text, position)
                require(match != null && match.range.first == position) { "Invalid JSON value." }
                position = match.range.last + 1
            }
        }
    }

    private fun literal(value: String) {
        require(text.startsWith(value, position)) { "Invalid JSON literal." }
        position += value.length
    }

    private fun string(): String {
        val start = position
        require(position < text.length && text[position++] == '"') { "Expected quoted string." }
        while (position < text.length) {
            when (val character = text[position++]) {
                '"' -> return JSONTokener(text.substring(start, position)).nextValue() as String
                '\\' -> {
                    require(position < text.length) { "Unterminated escape." }
                    when (text[position++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> {
                            require(position + 4 <= text.length && text.substring(position, position + 4).all { it in "0123456789abcdefABCDEF" }) { "Invalid Unicode escape." }
                            position += 4
                        }
                        else -> throw IllegalArgumentException("Invalid string escape.")
                    }
                }
                else -> require(character >= ' ') { "Unescaped control character." }
            }
        }
        throw IllegalArgumentException("Unterminated JSON string.")
    }

    private companion object {
        val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    }
}
