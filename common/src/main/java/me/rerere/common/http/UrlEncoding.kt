package me.rerere.common.http

private val hexDigits = "0123456789ABCDEF".toCharArray()

fun String.urlEncode(spaceAsPlus: Boolean = false): String {
    val output = StringBuilder()
    for (byte in encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        when {
            value.isUnreservedUrlByte() -> output.append(value.toChar())
            value == 0x20 && spaceAsPlus -> output.append('+')
            else -> {
                output.append('%')
                output.append(hexDigits[value shr 4])
                output.append(hexDigits[value and 0x0F])
            }
        }
    }
    return output.toString()
}

fun String.urlDecode(plusAsSpace: Boolean = false): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '%' && index + 2 < length -> {
                val high = this[index + 1].hexValue()
                val low = this[index + 2].hexValue()
                if (high >= 0 && low >= 0) {
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                } else {
                    bytes.addCharBytes(char)
                    index++
                }
            }
            char == '+' && plusAsSpace -> {
                bytes.add(0x20)
                index++
            }
            else -> {
                bytes.addCharBytes(char)
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun Int.isUnreservedUrlByte(): Boolean {
    return this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code ||
        this == '.'.code ||
        this == '_'.code ||
        this == '~'.code
}

private fun Char.hexValue(): Int {
    return when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> -1
    }
}

private fun MutableList<Byte>.addCharBytes(char: Char) {
    addAll(char.toString().encodeToByteArray().asList())
}
