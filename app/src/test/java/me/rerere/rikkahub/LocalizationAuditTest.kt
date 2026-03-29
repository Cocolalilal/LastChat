package me.rerere.rikkahub

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationAuditTest {
    @Test
    fun `zh-Hans resources stay in sync with base strings`() {
        auditLocale(localeName = "zh-Hans", localeDir = "values-b+zh+Hans")
    }

    @Test
    fun `ar resources stay in sync with base strings`() {
        auditLocale(localeName = "ar", localeDir = "values-ar")
    }

    private fun auditLocale(localeName: String, localeDir: String) {
        auditModule(
            moduleName = "app($localeName)",
            baseDir = File("src/main/res/values"),
            targetDir = File("src/main/res/$localeDir")
        )
        auditModule(
            moduleName = "search($localeName)",
            baseDir = File("../search/src/main/res/values"),
            targetDir = File("../search/src/main/res/$localeDir")
        )
    }

    private fun auditModule(moduleName: String, baseDir: File, targetDir: File) {
        val baseStrings = readStrings(baseDir).filterValues { it.translatable }
        val targetStrings = readStrings(targetDir)

        val missingKeys = (baseStrings.keys - targetStrings.keys).sorted()
        val placeholderMismatches = baseStrings.keys
            .intersect(targetStrings.keys)
            .sorted()
            .mapNotNull { key ->
                val basePlaceholders = placeholderCounts(baseStrings.getValue(key).value)
                val targetPlaceholders = placeholderCounts(targetStrings.getValue(key).value)
                if (basePlaceholders == targetPlaceholders) {
                    null
                } else {
                    "$key -> base=$basePlaceholders target=$targetPlaceholders"
                }
            }

        val failures = buildList {
            if (missingKeys.isNotEmpty()) {
                add("Missing keys (${missingKeys.size}): ${missingKeys.joinToString(", ")}")
            }
            if (placeholderMismatches.isNotEmpty()) {
                add(
                    "Placeholder mismatches (${placeholderMismatches.size}): ${
                        placeholderMismatches.joinToString("; ")
                    }"
                )
            }
        }

        assertTrue(
            buildString {
                append("Localization audit failed for ")
                append(moduleName)
                append(". ")
                append(failures.joinToString(" | "))
            },
            failures.isEmpty()
        )
    }

    private fun readStrings(valuesDir: File): Map<String, ResourceString> {
        require(valuesDir.isDirectory) { "Missing resource directory: ${valuesDir.path}" }

        val factory = DocumentBuilderFactory.newInstance()
        val files = valuesDir.listFiles { file ->
            file.isFile && file.name.startsWith("strings") && file.name.endsWith(".xml")
        }?.sortedBy { it.name }.orEmpty()

        val result = linkedMapOf<String, ResourceString>()
        for (file in files) {
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(file)
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val node = children.item(index)
                if (node !is Element || node.tagName != "string") {
                    continue
                }
                val key = node.getAttribute("name")
                result[key] = ResourceString(
                    value = node.textContent ?: "",
                    translatable = node.getAttribute("translatable") != "false"
                )
            }
        }
        return result
    }

    private fun placeholderCounts(value: String): Map<String, Int> {
        return PLACEHOLDER_REGEX
            .findAll(value)
            .map { it.value }
            .groupingBy { it }
            .eachCount()
    }

    private data class ResourceString(
        val value: String,
        val translatable: Boolean
    )

    private companion object {
        private val PLACEHOLDER_REGEX =
            Regex("%(?:\\d+\\$)?[-+#, 0]*(?:\\d+)?(?:\\.\\d+)?[a-zA-Z]")
    }
}
