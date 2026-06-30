package me.rerere.common.http

fun String.unescapeJsonStringContent(): String {
    val input = trim()
    val output = StringBuilder(input.length)
    var index = 0
    while (index < input.length) {
        val char = input[index]
        if (char != '\\' || index == input.lastIndex) {
            output.append(char)
            index++
            continue
        }

        index++
        when (val escaped = input[index]) {
            '"' -> output.append('"')
            '\\' -> output.append('\\')
            '/' -> output.append('/')
            'b' -> output.append('\b')
            'f' -> output.append('\u000C')
            'n' -> output.append('\n')
            'r' -> output.append('\r')
            't' -> output.append('\t')
            'u' -> {
                val hexStart = index + 1
                val hexEnd = hexStart + 4
                if (hexEnd <= input.length) {
                    val code = input.substring(hexStart, hexEnd).hexToIntOrNull()
                    if (code != null) {
                        output.append(code.toChar())
                        index = hexEnd - 1
                    } else {
                        output.append("\\u")
                    }
                } else {
                    output.append("\\u")
                }
            }
            else -> output.append(escaped)
        }
        index++
    }
    return output.toString()
}

private fun String.hexToIntOrNull(): Int? {
    var result = 0
    for (char in this) {
        val digit = when (char) {
            in '0'..'9' -> char.code - '0'.code
            in 'a'..'f' -> char.code - 'a'.code + 10
            in 'A'..'F' -> char.code - 'A'.code + 10
            else -> return null
        }
        result = result * 16 + digit
    }
    return result
}
