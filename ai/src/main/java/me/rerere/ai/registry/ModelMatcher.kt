package me.rerere.ai.registry

interface ModelMatcher {
    fun match(modelId: String): Boolean

    operator fun plus(other: ModelMatcher): ModelMatcher {
        return OrModelMatcher(listOf(this, other))
    }

    companion object {
        fun exact(id: String): ModelMatcher = ExactModelMatcher(id)

        fun regex(regex: String): ModelMatcher = RegexModelMatcher(regex.toRegex(RegexOption.IGNORE_CASE))

        fun containsRegex(regex: String, negated: Boolean = false): ModelMatcher = ContainsRegexModelMatcher(
            regex = regex.toRegex(RegexOption.IGNORE_CASE),
            negated = negated
        )

        fun regex(regex: Regex): ModelMatcher = RegexModelMatcher(regex)

        fun or(vararg matchers: ModelMatcher): ModelMatcher = OrModelMatcher(matchers.toList())

        fun and(vararg matchers: ModelMatcher): ModelMatcher = AndModelMatcher(matchers.toList())
    }
}

infix fun ModelMatcher.and(other: ModelMatcher): ModelMatcher = ModelMatcher.and(this, other)

infix fun ModelMatcher.or(other: ModelMatcher): ModelMatcher = ModelMatcher.or(this, other)

private class OrModelMatcher(
    val matchers: List<ModelMatcher>
) : ModelMatcher {
    override fun match(modelId: String): Boolean {
        return matchers.any { it.match(modelId) }
    }
}

private class AndModelMatcher(
    val matchers: List<ModelMatcher>
) : ModelMatcher {
    override fun match(modelId: String): Boolean {
        return matchers.all { it.match(modelId) }
    }
}

private class RegexModelMatcher(
    val regex: Regex
) : ModelMatcher {
    override fun match(modelId: String): Boolean {
        return regex.matches(modelId)
    }
}

private class ContainsRegexModelMatcher(
    val regex: Regex,
    val negated: Boolean = false
) : ModelMatcher {
    override fun match(modelId: String): Boolean {
        return if (negated) {
            !regex.containsMatchIn(modelId)
        } else {
            regex.containsMatchIn(modelId)
        }
    }
}

private class ExactModelMatcher(
    val id: String
) : ModelMatcher {
    override fun match(modelId: String): Boolean {
        return modelId == id
    }
}
