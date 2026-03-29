package me.rerere.rikkahub.utils

import java.util.Locale

enum class BidiDirection {
    Ltr,
    Rtl,
}

fun resolveBidiDirection(
    text: CharSequence,
    fallbackLocale: Locale = Locale.getDefault(),
): BidiDirection {
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE -> return BidiDirection.Ltr

            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE -> return BidiDirection.Rtl
        }
        index += Character.charCount(codePoint)
    }
    return if (fallbackLocale.isRtlLanguage()) BidiDirection.Rtl else BidiDirection.Ltr
}

fun Locale.isRtlLanguage(): Boolean {
    return language.lowercase(Locale.ROOT) in RTL_LANGUAGE_CODES
}

fun BidiDirection.toHtmlDir(): String = if (this == BidiDirection.Rtl) "rtl" else "ltr"

private val RTL_LANGUAGE_CODES = setOf(
    "ar",
    "fa",
    "he",
    "ps",
    "sd",
    "ug",
    "ur",
    "yi",
)
