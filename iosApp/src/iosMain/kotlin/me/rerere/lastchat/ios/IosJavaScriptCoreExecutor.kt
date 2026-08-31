package me.rerere.lastchat.ios

import platform.JavaScriptCore.JSContext

class IosJavaScriptCoreExecutor : IosJavaScriptExecutor {
    override fun execute(code: String): String {
        val context = JSContext()
        val wrapped = """
            (() => {
              const value = (0, eval)(${code.toJavaScriptStringLiteral()});
              if (value !== null && typeof value === "object") {
                return JSON.stringify(value);
              }
              return String(value);
            })()
        """.trimIndent()
        val value = context.evaluateScript(wrapped)
        context.exception?.let { exception ->
            error(exception.toString())
        }
        return value?.toString() ?: "null"
    }
}

private fun String.toJavaScriptStringLiteral(): String = buildString(length + 2) {
    append('"')
    this@toJavaScriptStringLiteral.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}
