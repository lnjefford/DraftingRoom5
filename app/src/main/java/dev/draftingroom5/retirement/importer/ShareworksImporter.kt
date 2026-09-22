package dev.draftingroom5.retirement.importer

import dev.draftingroom5.retirement.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import kotlin.math.sqrt

enum class WorkbookFailure { INVALID, RECALCULATE, TOO_LARGE, UNSUPPORTED, CANCELLED, SAVE_FAILED, CONFLICT }
class WorkbookRejected(val reason: WorkbookFailure) : Exception(reason.name)
class ValidatedEpicCandidate internal constructor(val workbook: EpicWorkbook, val digest: String) {
    override fun toString() = "ValidatedEpicCandidate([private])"
}

/** Bounded, in-memory OOXML subset. VBA and formulas are never executed or persisted. */
class ShareworksImporter {
    fun parse(input: InputStream, cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }): ValidatedEpicCandidate {
        try {
            val bytes = input.use { bounded(it, MAX_COMPRESSED, cancelled) }
            try {
                val parts = archive(bytes, cancelled)
                try { return decode(parts, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, cancelled) }
                finally { parts.values.forEach { it.fill(0) } }
            } finally { bytes.fill(0) }
        } catch (failure: WorkbookRejected) { throw failure }
        catch (error: Exception) {
            // SAX implementations may wrap handler exceptions; preserve only our safe category.
            val safe = generateSequence<Throwable>(error) { it.cause }.take(8).filterIsInstance<WorkbookRejected>().firstOrNull()
            throw WorkbookRejected(safe?.reason ?: WorkbookFailure.INVALID)
        }
    }

    private fun decode(parts: Map<String, ByteArray>, digest: String, cancelled: () -> Boolean): ValidatedEpicCandidate {
        fun xml(path: String, start: (String, Attributes) -> Unit = { _, _ -> }, text: (String, String) -> Unit = { _, _ -> }) =
            xml(parts[path] ?: reject(), cancelled, start, text)
        var macroType = false
        xml("[Content_Types].xml", { tag, a -> if (tag == "Override" && a.getValue("PartName") == "/xl/workbook.xml") {
            macroType = a.getValue("ContentType") == "application/vnd.ms-excel.sheet.macroEnabled.main+xml"
        } })
        if (!macroType) throw WorkbookRejected(WorkbookFailure.UNSUPPORTED)
        val relationships = linkedMapOf<String, String>()
        xml("xl/_rels/workbook.xml.rels", { tag, a -> if (tag == "Relationship") {
            val type = a.getValue("Type") ?: reject()
            if (a.getValue("TargetMode") == "External" || type.endsWith("/externalLink")) reject()
            if (type.endsWith("/worksheet")) {
                val target = a.getValue("Target") ?: reject()
                val path = if (target.startsWith("/xl/")) target.drop(1) else "xl/$target"
                require(path.matches(Regex("xl/worksheets/[A-Za-z0-9_-]+\\.xml")))
                require(relationships.size < 2000)
                require(relationships.put(a.getValue("Id") ?: reject(), path) == null)
            }
        } })
        val sheets = linkedMapOf<String, String>()
        var date1904 = false
        xml("xl/workbook.xml", { tag, a -> when (tag) {
            "workbookPr" -> date1904 = a.getValue("date1904") in setOf("1", "true")
            "calcPr" -> {
                if (a.getValue("calcMode") in setOf("manual", "autoNoTable") ||
                    listOf("fullCalcOnLoad", "forceFullCalc").any { a.getValue(it) in setOf("1", "true") } ||
                    a.getValue("calcCompleted") in setOf("0", "false")) throw WorkbookRejected(WorkbookFailure.RECALCULATE)
            }
            "sheet" -> {
                val name = a.getValue("name") ?: reject()
                require(name.length in 1..120 && sheets.size < 2000)
                val id = a.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") ?: reject()
                require(sheets.put(name, relationships[id] ?: reject()) == null)
            }
        } })
        require(sheets.values.distinct().size == sheets.size)
        val strings = mutableListOf<String>()
        parts["xl/sharedStrings.xml"]?.let { bytes ->
            var current = StringBuilder()
            xml(bytes, cancelled, { tag, _ -> if (tag == "si") current = StringBuilder() }, { tag, value ->
                if (tag == "t") { current.append(value); require(current.length <= 4096) }
                if (tag == "si") { require(strings.size < 100_000); strings += current.toString() }
            })
        }
        fun cells(name: String): Map<String, Cell> {
            val result = linkedMapOf<String, Cell>()
            val seen = hashSetOf<String>()
            fun selected(address: String): Boolean {
                val column = address.takeWhile(Char::isLetter)
                val row = address.drop(column.length).toInt()
                return when (name) {
                    "2026 Consolidated Statement" -> address == "E6" || (row in 12..19 && column in setOf("B", "C", "E", "F", "G", "H", "I", "J", "L", "M"))
                    "Inputs and Summary" -> column == "C" && row in setOf(9, 10, 11, 12, 13, 38, 41, 44, 47)
                    "Detailed Projections" -> columnNumber(column) >= 3 && row in setOf(15, 19, 23, 24, 31, 36, 37, 46, 47, 48, 49, 51)
                    "Class B Share Price History" -> column == "D" && row >= 8
                    else -> false
                }
            }
            var address = ""; var type = ""; var formula = false; var cache: String? = null; var inline = StringBuilder()
            xml(sheets[name] ?: reject(), { tag, a -> when (tag) {
                "c" -> { address = a.getValue("r") ?: reject(); require(address.matches(Regex("[A-Z]{1,3}[1-9][0-9]{0,6}")))
                    type = a.getValue("t") ?: "n"; formula = false; cache = null; inline = StringBuilder() }
                "f" -> { formula = true; if (selected(address) && a.getValue("ca") in setOf("1", "true")) throw WorkbookRejected(WorkbookFailure.RECALCULATE) }
            } }, { tag, value -> when (tag) {
                "v" -> { require(cache == null); cache = value }
                "t" -> { inline.append(value); require(inline.length <= 4096) }
                "c" -> {
                    require(seen.size < 100_000 && seen.add(address))
                    if (selected(address)) {
                        val raw = when (type) { "s" -> strings.getOrNull(cache?.toIntOrNull() ?: reject()) ?: reject(); "inlineStr" -> inline.toString(); else -> cache }
                        result[address] = Cell(raw, type, formula)
                    }
                }
            } })
            return result
        }
        val consolidated = cells("2026 Consolidated Statement")
        val inputs = cells("Inputs and Summary")
        fun Map<String, Cell>.number(address: String) = (get(address) ?: reject()).number()
        fun Map<String, Cell>.money(address: String): Money = number(address).let { number ->
            val cents = number.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            require(cents in -MAX_ASSET_CENTS..MAX_ASSET_CENTS); Money(cents)
        }
        fun Map<String, Cell>.text(address: String) = (get(address) ?: reject()).text()
        fun totals(row: Int) = EpicTotals(consolidated.number("C$row").plain(), consolidated.number("E$row").plain(), consolidated.money("F$row"),
            consolidated.number("G$row").plain(), consolidated.money("H$row"), consolidated.money("I$row"), consolidated.money("J$row"), consolidated.money("L$row"), consolidated.money("M$row"))
        val breakdown = (12..18).mapNotNull { row ->
            val name = consolidated["B$row"]?.text()?.replace('\n', ' ')?.trim()
            if (name.isNullOrEmpty()) {
                require(listOf("C", "E", "F", "G", "H", "I", "J", "L", "M").none { consolidated["$it$row"]?.raw?.isNotEmpty() == true })
                null
            } else EpicBreakdown(name, totals(row))
        }
        val dateCell = inputs["C38"] ?: reject()
        val date = if (dateCell.type == "n") {
            val serial = dateCell.number().longValueExact(); require(serial in 1..219_000 && (date1904 || serial != 60L))
            (if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 31)).plusDays(serial - if (!date1904 && serial > 60) 1 else 0)
        } else LocalDate.parse(dateCell.text())
        val projection = EpicProjection(date, inputs.number("C41").plain(), inputs.number("C47").plain(), inputs.text("C44"),
            inputs.money("C9"), inputs.money("C10"), inputs.money("C11"), inputs.money("C12"), inputs.money("C13"))
        val annual = if ("Detailed Projections" !in sheets) emptyList() else {
            val detail = cells("Detailed Projections")
            val columns = detail.keys.filter { it.matches(Regex("[A-Z]+15")) }.map { it.dropLast(2) }
                .filter { columnNumber(it) >= 3 && !detail["${it}15"]?.raw.isNullOrBlank() }.sortedBy(::columnNumber)
            require(columns.size <= 500 && columns.all { columnNumber(it) <= 502 })
            require(detail.filterValues { !it.raw.isNullOrBlank() || it.formula }.keys.all { it.takeWhile(Char::isLetter) in columns })
            columns.map { col -> EpicYear(detail.number("${col}15").intValueExact(), detail.number("${col}19").plain(),
                detail.money("${col}23"), detail.money("${col}24"), detail.money("${col}31"), detail.money("${col}36"), detail.money("${col}37"),
                detail.money("${col}46"), detail.money("${col}47"), detail.money("${col}48"), detail.money("${col}49"), detail.money("${col}51")) }
        }
        val volatility = if ("Class B Share Price History" !in sheets) null else {
            val history = cells("Class B Share Price History")
            val observations = history.filterKeys { it.matches(Regex("D[0-9]+")) && it.drop(1).toInt() >= 8 }
                .filterValues { !it.raw.isNullOrBlank() || it.formula }.values.map { it.number().toDouble().also { v -> require(v in -1.0..10.0) } }
            if (observations.size < 2) null else {
                val mean = observations.average()
                BigDecimal.valueOf(sqrt(observations.sumOf { (it - mean) * (it - mean) } / (observations.size - 1)) * 100).plain()
            }
        }
        val book = EpicWorkbook(consolidated.money("E6"), totals(19), breakdown, projection, annual, volatility)
        validateEpicWorkbook(book)
        return ValidatedEpicCandidate(book, digest)
    }

    private data class Cell(val raw: String?, val type: String, val formula: Boolean) {
        fun number(): BigDecimal {
            if (formula && (raw.isNullOrBlank() || type !in setOf("n", ""))) throw WorkbookRejected(WorkbookFailure.RECALCULATE)
            require(type in setOf("n", "s", "inlineStr", "str"))
            val value = raw?.trim() ?: reject()
            require(value.length <= 80)
            // OOXML numeric cells may use scientific notation; decimal text cells retain strict money syntax.
            return if (type == "n") {
                require(value.matches(Regex("-?\\d+(?:\\.\\d+)?(?:[Ee][+-]?\\d{1,3})?")))
                BigDecimal(value).also { require(it.scale() in -16..16 && it.abs() <= BigDecimal("1000000000000")) }
            } else {
                require(value.matches(Regex("-?\\$?(?:\\d+|[1-9]\\d{0,2}(?:,\\d{3})+)(?:\\.\\d+)?")))
                BigDecimal(value.replace("$", "").replace(",", ""))
            }
        }
        fun text(): String {
            if (formula && raw.isNullOrBlank()) throw WorkbookRejected(WorkbookFailure.RECALCULATE)
            require(type in setOf("s", "inlineStr", "str", "d")); return raw ?: reject()
        }
    }

    private fun archive(bytes: ByteArray, cancelled: () -> Boolean): Map<String, ByteArray> {
        fun u16(p: Int): Int { require(p >= 0 && p + 2 <= bytes.size); return (bytes[p].toInt() and 255) or ((bytes[p + 1].toInt() and 255) shl 8) }
        fun u32(p: Int): Long = u16(p).toLong() or (u16(p + 2).toLong() shl 16)
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65_557)).firstOrNull {
            u32(it) == 0x06054b50L && it + 22 + u16(it + 20) == bytes.size
        } ?: reject()
        require(u16(end + 4) == 0 && u16(end + 6) == 0 && u16(end + 8) == u16(end + 10))
        val count = u16(end + 10); require(count in 1..2000)
        val centralStart = u32(end + 16).toInt(); require(centralStart >= 0 && centralStart.toLong() + u32(end + 12) == end.toLong())
        var p = centralStart; var expanded = 0L
        val entries = linkedMapOf<String, Triple<Long, Long, Long>>()
        repeat(count) {
            require(u32(p) == 0x02014b50L && u16(p + 8) and 1 == 0 && u16(p + 10) in setOf(0, 8))
            val compressed = u32(p + 20); val size = u32(p + 24)
            if (size > MAX_EXPANDED || size > maxOf(1L, compressed) * 100) throw WorkbookRejected(WorkbookFailure.TOO_LARGE)
            expanded += size; if (expanded > MAX_EXPANDED) throw WorkbookRejected(WorkbookFailure.TOO_LARGE)
            val length = u16(p + 28); require(length in 1..240 && p + 46 + length <= end)
            val name = bytes.copyOfRange(p + 46, p + 46 + length).toString(Charsets.UTF_8)
            require(!name.startsWith('/') && !name.contains('\\') && ':' !in name && name.split('/').none { it == ".." || it == "." } && name.none(Char::isISOControl))
            require(!name.startsWith("xl/externalLinks/"))
            val offset = u32(p + 42).toInt(); require(offset >= 0 && offset < centralStart && u32(offset) == 0x04034b50L)
            require(entries.put(name, Triple(size, compressed, u32(p + 16))) == null)
            p += 46 + length + u16(p + 30) + u16(p + 32); require(p <= end)
        }
        require(p == end)
        val retained = linkedMapOf<String, ByteArray>()
        var retainedBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val seen = hashSetOf<String>()
                while (true) {
                    if (cancelled()) throw WorkbookRejected(WorkbookFailure.CANCELLED)
                    val entry = zip.nextEntry ?: break
                    val expected = entries[entry.name] ?: reject(); require(seen.add(entry.name))
                    val keep = entry.name in setOf("[Content_Types].xml", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/sharedStrings.xml") ||
                        entry.name.matches(Regex("xl/worksheets/[A-Za-z0-9_-]+\\.xml"))
                    if (keep) {
                        retainedBytes += expected.first
                        if (retainedBytes > MAX_RETAINED_XML) throw WorkbookRejected(WorkbookFailure.TOO_LARGE)
                    }
                    val output = if (keep) ByteArrayOutputStream() else null
                    var total = 0L; val buffer = ByteArray(8192)
                    while (true) {
                        if (cancelled()) { throw WorkbookRejected(WorkbookFailure.CANCELLED) }
                        val n = zip.read(buffer)
                        if (n == -1) { break }
                        total += n
                        if (total > expected.first || (keep && total > MAX_XML)) { throw WorkbookRejected(WorkbookFailure.TOO_LARGE) }
                        output?.write(buffer, 0, n)
                    }
                    require(total == expected.first && entry.crc == expected.third && entry.compressedSize == expected.second)
                    if (keep) retained[entry.name] = output!!.toByteArray()
                    zip.closeEntry()
                }
                require(seen == entries.keys)
            }
            return retained
        } catch (e: Exception) { retained.values.forEach { it.fill(0) }; throw e }
    }

    private fun xml(bytes: ByteArray, cancelled: () -> Boolean, start: (String, Attributes) -> Unit, end: (String, String) -> Unit) {
        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val handler = object : DefaultHandler2() {
            val text = ArrayDeque<StringBuilder>(); var nodes = 0
            override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("Invalid XML") }
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource { throw SAXException("Invalid XML") }
            override fun startElement(uri: String?, local: String, qName: String?, attributes: Attributes) {
                if (cancelled()) throw WorkbookRejected(WorkbookFailure.CANCELLED)
                require(++nodes <= 500_000 && text.size < 64 && attributes.length <= 100)
                text.addLast(StringBuilder()); start(local, attributes)
            }
            override fun characters(ch: CharArray, offset: Int, length: Int) {
                if (text.isNotEmpty()) { val current = text.last(); require(current.length + length <= 16_384); current.append(ch, offset, length) }
            }
            override fun endElement(uri: String?, local: String, qName: String?) { end(local, text.removeLast().toString()) }
            override fun error(e: org.xml.sax.SAXParseException) { throw SAXException("Invalid XML") }
            override fun fatalError(e: org.xml.sax.SAXParseException) { throw SAXException("Invalid XML") }
        }
        reader.contentHandler = handler; reader.errorHandler = handler; reader.entityResolver = handler
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.parse(InputSource(ByteArrayInputStream(bytes)))
    }

    companion object {
        const val MAX_COMPRESSED = 20 * 1024 * 1024
        const val MAX_EXPANDED = 100 * 1024 * 1024
        const val MAX_XML = 16 * 1024 * 1024
        const val MAX_RETAINED_XML = 32 * 1024 * 1024
        private fun bounded(input: InputStream, max: Int, cancelled: () -> Boolean): ByteArray {
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                if (cancelled()) { throw WorkbookRejected(WorkbookFailure.CANCELLED) }
                val n = input.read(buffer)
                if (n == -1) { break }
                if (out.size().toLong() + n > max) { throw WorkbookRejected(WorkbookFailure.TOO_LARGE) }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
        private fun reject(): Nothing = throw WorkbookRejected(WorkbookFailure.INVALID)
        private fun columnNumber(column: String) = column.fold(0) { value, ch -> value * 26 + (ch - 'A' + 1) }
        private fun BigDecimal.plain() = stripTrailingZeros().toPlainString()
    }
}
