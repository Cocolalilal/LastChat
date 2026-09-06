package me.rerere.lastchat.ios.backup

/**
 * Minimal read-only SQLite database file reader, pure Kotlin so it works on every target.
 * Supports what the Android backup needs: table B-tree walks (interior and leaf pages),
 * the record format, and overflow-page payload chains. Values are surfaced as text; NULL
 * and BLOB columns surface as null.
 */
internal class IosSqliteFile(private val data: ByteArray) {
    private val pageSize: Int
    private val usableSize: Int

    init {
        require(data.size >= 100 && data.decodeToString(0, 15) == "SQLite format 3") {
            "Not a SQLite database"
        }
        val rawPageSize = u16be(16)
        pageSize = if (rawPageSize == 1) 65536 else rawPageSize
        usableSize = pageSize - (data[20].toInt() and 0xFF)
    }

    fun hasTable(name: String): Boolean = tables().any { it.name.equals(name, ignoreCase = true) }

    /** Reads every row of [tableName]; rows map column name to text value (null = NULL/BLOB).
     *  The rowid is substituted for the INTEGER PRIMARY KEY alias column, matching SQLite semantics. */
    fun readTable(name: String, onRow: (Map<String, String?>) -> Unit): Boolean {
        val table = tables().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        val columns = parseCreateTableColumns(table.sql) ?: return false
        val rowIdAlias = parseRowIdAliasColumn(table.sql)
        walkTable(table.rootPage) { rowId, record ->
            val values = record.toMutableList()
            if (rowIdAlias != null) {
                columns.indexOf(rowIdAlias).takeIf { it >= 0 }?.let { index ->
                    if (values.getOrNull(index) == null) values[index] = rowId.toString()
                }
            }
            if (values.size >= columns.size) {
                onRow(columns.indices.associate { columns[it] to values[it] })
            }
        }
        return true
    }

    private data class TableEntry(val name: String, val rootPage: Int, val sql: String)

    private fun tables(): List<TableEntry> {
        val entries = mutableListOf<TableEntry>()
        walkTable(SQLITE_MASTER_ROOT_PAGE) { _, record ->
            val type = record.getOrNull(0)
            val name = record.getOrNull(1)
            val rootPage = record.getOrNull(3)?.toLongOrNull()
            val sql = record.getOrNull(4)
            if (type == "table" && !name.isNullOrBlank() && rootPage != null && rootPage > 0) {
                entries.add(TableEntry(name, rootPage.toInt(), sql.orEmpty()))
            }
        }
        return entries
    }

    private fun walkTable(rootPage: Int, onRecord: (rowId: Long, record: List<String?>) -> Unit) {
        val visited = mutableSetOf<Int>()
        fun walk(pageNumber: Int) {
            if (pageNumber <= 0 || (pageNumber - 1) * pageSize >= data.size) return
            if (!visited.add(pageNumber)) return
            val pageStart = (pageNumber - 1) * pageSize
            val headerOffset = if (pageNumber == 1) 100 else 0
            when (data[pageStart + headerOffset].toInt() and 0xFF) {
                PAGE_INTERIOR_TABLE -> {
                    val cellCount = u16be(pageStart + headerOffset + 3)
                    val pointerArray = pageStart + headerOffset + 12
                    for (index in 0 until cellCount) {
                        walk(u32be(pageStart + u16be(pointerArray + 2 * index)).toInt())
                    }
                    walk(u32be(pageStart + headerOffset + 8).toInt())
                }
                PAGE_LEAF_TABLE -> {
                    val cellCount = u16be(pageStart + headerOffset + 3)
                    val pointerArray = pageStart + headerOffset + 8
                    for (index in 0 until cellCount) {
                        val cellOffset = pageStart + u16be(pointerArray + 2 * index)
                        val (payloadSize, sizeBytes) = readVarint(cellOffset)
                        val (rowId, rowIdBytes) = readVarint(cellOffset + sizeBytes)
                        val payloadStart = cellOffset + sizeBytes + rowIdBytes
                        if (payloadSize in 1..data.size.toLong()) {
                            onRecord(rowId, decodeRecord(readPayload(payloadStart, payloadSize.toInt())))
                        }
                    }
                }
            }
        }
        walk(rootPage)
    }

    private fun readPayload(payloadStart: Int, payloadSize: Int): ByteArray {
        val maxLocal = usableSize - 35
        if (payloadSize <= maxLocal) {
            return data.copyOfRange(payloadStart, payloadStart + payloadSize)
        }
        val minLocal = ((usableSize - 12) * 32 / 255) - 23
        val threshold = minLocal + ((payloadSize - minLocal) % (usableSize - 4))
        val localSize = if (threshold <= maxLocal) threshold else minLocal
        val buffer = ByteArray(payloadSize)
        data.copyInto(buffer, 0, payloadStart, payloadStart + localSize)
        var filled = localSize
        var overflowPage = u32be(payloadStart + localSize).toInt()
        while (overflowPage != 0 && filled < payloadSize) {
            val overflowStart = (overflowPage - 1) * pageSize
            val next = u32be(overflowStart).toInt()
            val chunk = minOf(usableSize - 4, payloadSize - filled)
            data.copyInto(buffer, filled, overflowStart + 4, overflowStart + 4 + chunk)
            filled += chunk
            overflowPage = next
        }
        return buffer
    }

    private fun decodeRecord(payload: ByteArray): List<String?> {
        val (headerSize, headerSizeBytes) = readVarintFrom(payload, 0)
        var headerCursor = headerSizeBytes
        val serialTypes = mutableListOf<Long>()
        while (headerCursor < headerSize) {
            val (serialType, used) = readVarintFrom(payload, headerCursor)
            serialTypes.add(serialType)
            headerCursor += used
        }
        var bodyCursor = headerSize.toInt()
        return serialTypes.map { serialType ->
            val value = decodeSerialValue(payload, bodyCursor, serialType)
            bodyCursor += serialTypeSize(serialType)
            value
        }
    }

    private fun serialTypeSize(serialType: Long): Int = when (serialType) {
        0L, 8L, 9L, 10L, 11L -> 0
        1L -> 1
        2L -> 2
        3L -> 3
        4L -> 4
        5L -> 6
        6L, 7L -> 8
        else -> ((serialType - 12) / 2).toInt()
    }

    private fun decodeSerialValue(payload: ByteArray, start: Int, serialType: Long): String? = when (serialType) {
        0L, 10L, 11L -> null
        8L -> "0"
        9L -> "1"
        in 1L..6L -> readIntBE(payload, start, serialTypeSize(serialType)).toString()
        7L -> null // float columns are not used by the backed-up tables
        else -> {
            val size = serialTypeSize(serialType)
            if (serialType % 2 == 0L) {
                null // BLOB values are not surfaced as text
            } else {
                payload.decodeToString(start, start + size)
            }
        }
    }

    private fun readVarint(offset: Int): Pair<Long, Int> = readVarintFrom(data, offset)

    private fun readVarintFrom(source: ByteArray, offset: Int): Pair<Long, Int> {
        var value = 0L
        var index = 0
        while (index < 8) {
            val byte = source[offset + index].toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F).toLong()
            index++
            if (byte and 0x80 == 0) return value to index
        }
        val byte = source[offset + 8].toInt() and 0xFF
        value = (value shl 8) or byte.toLong()
        return value to 9
    }

    private fun readIntBE(source: ByteArray, start: Int, size: Int): Long {
        var value = if (source[start].toInt() and 0x80 != 0) -1L else 0L
        for (index in 0 until size) {
            value = (value shl 8) or (source[start + index].toLong() and 0xFF)
        }
        return value
    }

    private fun u16be(offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun u32be(offset: Int): Long =
        (u16be(offset).toLong() shl 16) or u16be(offset + 2).toLong()

    private companion object {
        const val SQLITE_MASTER_ROOT_PAGE = 1
        const val PAGE_INTERIOR_TABLE = 0x05
        const val PAGE_LEAF_TABLE = 0x0D
    }
}

/** Finds the rowid-alias column: a column declared exactly `INTEGER PRIMARY KEY`
 *  (SQLite stores NULL in its record slot and reads back the rowid instead). */
internal fun parseRowIdAliasColumn(sql: String): String? {
    val open = sql.indexOf('(')
    if (open == -1) return null
    var depth = 0
    var close = -1
    for (index in open until sql.length) {
        when (sql[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    close = index
                    break
                }
            }
        }
    }
    if (close == -1) return null
    val body = sql.substring(open + 1, close)
    val parts = mutableListOf<String>()
    var current = StringBuilder()
    var level = 0
    for (char in body) {
        when (char) {
            '(' -> { level++; current.append(char) }
            ')' -> { level--; current.append(char) }
            ',' -> if (level == 0) { parts.add(current.toString()); current = StringBuilder() } else current.append(char)
            else -> current.append(char)
        }
    }
    parts.add(current.toString())
    for (partRaw in parts) {
        val (name, rest) = parseIdentifierAndRest(partRaw) ?: continue
        if (name.isBlank()) continue
        val definition = rest.uppercase().trim()
        if (definition.startsWith("INTEGER") && definition.contains("PRIMARY KEY")) {
            return name
        }
    }
    return null
}

/** Extracts column names in declaration order from a Room-generated CREATE TABLE statement. */
internal fun parseCreateTableColumns(sql: String): List<String>? {
    val open = sql.indexOf('(')
    if (open == -1) return null
    var depth = 0
    var close = -1
    for (index in open until sql.length) {
        when (sql[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    close = index
                    break
                }
            }
        }
    }
    if (close == -1) return null
    val body = sql.substring(open + 1, close)
    val parts = mutableListOf<String>()
    var current = StringBuilder()
    var level = 0
    for (char in body) {
        when (char) {
            '(' -> { level++; current.append(char) }
            ')' -> { level--; current.append(char) }
            ',' -> if (level == 0) { parts.add(current.toString()); current = StringBuilder() } else current.append(char)
            else -> current.append(char)
        }
    }
    parts.add(current.toString())
    val columns = mutableListOf<String>()
    for (partRaw in parts) {
        val trimmed = partRaw.trim()
        if (trimmed.isEmpty()) continue
        val upper = trimmed.uppercase()
        if (upper.startsWith("PRIMARY") || upper.startsWith("UNIQUE") || upper.startsWith("CHECK") ||
            upper.startsWith("FOREIGN") || upper.startsWith("CONSTRAINT")
        ) {
            continue
        }
        val parsed = parseIdentifierAndRest(trimmed) ?: continue
        if (parsed.first.isBlank()) continue
        columns.add(parsed.first)
    }
    return columns
}

/** Splits a column definition into its identifier (handling `` ` ``, `"`, and `[name]` quoting)
 *  and the remainder of the definition. */
private fun parseIdentifierAndRest(part: String): Pair<String, String>? {
    val trimmed = part.trim()
    if (trimmed.isEmpty()) return null
    return when {
        trimmed.startsWith('`') -> {
            val name = trimmed.removePrefix("`").substringBefore('`')
            name to trimmed.removePrefix("`").substringAfter('`', "")
        }
        trimmed.startsWith('"') -> {
            val name = trimmed.removePrefix("\"").substringBefore('"')
            name to trimmed.removePrefix("\"").substringAfter('"', "")
        }
        trimmed.startsWith('[') -> {
            val name = trimmed.removePrefix("[").substringBefore(']')
            name to trimmed.removePrefix("[").substringAfter(']', "")
        }
        else -> {
            val name = trimmed.substringBefore(' ').substringBefore('(').trim()
            name to trimmed.removePrefix(name)
        }
    }
}
