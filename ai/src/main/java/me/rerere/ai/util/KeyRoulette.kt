package me.rerere.ai.util

interface KeyRoulette {
    fun next(keys: String): String

    companion object {
        fun default(): KeyRoulette = DefaultKeyRoulette()
    }
}

private val SPLIT_KEY_REGEX=  "[\\s,]+".toRegex()//空格换行和逗号

private fun splitKey(key: String): List<String> {
    return key
        .split(SPLIT_KEY_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private class DefaultKeyRoulette : KeyRoulette {
    override fun next(keys: String): String {
        val keyList = splitKey(keys)
        return if (keyList.isNotEmpty()) {
            keyList.random()
        } else {
            keys
        }
    }
}
