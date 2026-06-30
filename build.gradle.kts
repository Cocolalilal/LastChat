// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

val iosCandidateModules = listOf(
    "shared",
    "ai",
    "common",
    "search",
    "highlight",
    "document",
    "tts",
    "app"
)

val iosSharedCandidatePackages = listOf(
    "me.rerere.common.platform",
    "me.rerere.ai.core",
    "me.rerere.ai.provider",
    "me.rerere.ai.registry",
    "me.rerere.ai.ui",
    "me.rerere.ai.util",
    "me.rerere.common.cache",
    "me.rerere.common.calendar",
    "me.rerere.common.html",
    "me.rerere.common.http",
    "me.rerere.highlight",
    "me.rerere.search",
    "me.rerere.tts.model",
    "me.rerere.tts.provider",
    "me.rerere.tts.provider.providers"
)

val iosSharedCandidateExclusions = listOf(
    "me.rerere.common.platform.android",
    "me.rerere.highlight.android",
    "me.rerere.tts.provider.android",
    "me.rerere.tts.provider.providers.android"
)

val iosPortabilityBlockers = linkedMapOf(
    "Android framework" to listOf(
        "android.",
        "androidx.activity.",
        "androidx.appcompat.",
        "androidx.camera.",
        "androidx.core.",
        "androidx.datastore.",
        "androidx.glance.",
        "androidx.lifecycle.",
        "androidx.media3.",
        "androidx.navigation.",
        "androidx.paging.",
        "androidx.palette.",
        "androidx.room.",
        "androidx.security.",
        "androidx.work.",
        "com.google.android.",
        "com.google.firebase."
    ),
    "JVM-only I/O/runtime" to listOf(
        "java.io.",
        "java.nio.",
        "java.math.",
        "java.awt.",
        "javax.",
        "org.w3c.dom.",
        "org.xml.",
        "java.time.",
        "java.util.concurrent.",
        "java.util.Locale",
        "java.util.Base64",
        "java.net.URI",
        "java.net.URLEncoder",
        "java.security."
    ),
    "Android/JVM networking" to listOf(
        "okhttp3.",
        "retrofit2.",
        "org.jsoup."
    ),
    "Android resource UI" to listOf(
        "androidx.compose.ui.res."
    ),
    "JVM-only helper libraries" to listOf(
        "org.apache.commons."
    )
)

val iosPortabilityLineBlockers = linkedMapOf(
    "JVM-only reflection/runtime usage" to listOf(
        "::class.java",
        "javaClass"
    )
)

tasks.register("iosPortabilityReport") {
    group = "verification"
    description = "Writes an iOS portability report without changing Android build outputs."

    val outputFile = layout.buildDirectory.file("reports/ios-portability.md")
    iosCandidateModules.forEach { moduleName ->
        inputs.dir(project(":$moduleName").projectDir.resolve("src"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    outputs.file(outputFile)

    doLast {
        val importRegex = Regex("""^\s*import\s+([^ ]+)""")
        val packageRegex = Regex("""^\s*package\s+([^ ]+)""")
        val findings = mutableListOf<PortabilityFinding>()

        iosCandidateModules.forEach { moduleName ->
            val moduleDir = project(":$moduleName").projectDir
            val sourceDirs = listOf(
                moduleDir.resolve("src/main"),
                moduleDir.resolve("src/commonMain")
            ).filter { it.exists() }

            sourceDirs.forEach { sourceDir ->
                sourceDir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val lines = file.readLines()
                        val packageName = lines.firstNotNullOfOrNull { line ->
                            packageRegex.find(line)?.groupValues?.get(1)
                        }.orEmpty()
                        val sharedCandidate = iosSharedCandidatePackages.any { packageName == it || packageName.startsWith("$it.") } &&
                            iosSharedCandidateExclusions.none { packageName == it || packageName.startsWith("$it.") }

                        lines.forEachIndexed { index, line ->
                            importRegex.find(line)?.groupValues?.get(1)?.let { imported ->
                                iosPortabilityBlockers.forEach { (category, prefixes) ->
                                    if (prefixes.any { imported.startsWith(it) }) {
                                        findings += PortabilityFinding(
                                            module = moduleName,
                                            file = file.relativeTo(rootDir).invariantSeparatorsPath,
                                            line = index + 1,
                                            category = category,
                                            symbol = imported,
                                            sharedCandidate = sharedCandidate
                                        )
                                    }
                                }
                            }

                            iosPortabilityLineBlockers.forEach { (category, patterns) ->
                                patterns.filter { pattern -> line.contains(pattern) }.forEach { pattern ->
                                    findings += PortabilityFinding(
                                        module = moduleName,
                                        file = file.relativeTo(rootDir).invariantSeparatorsPath,
                                        line = index + 1,
                                        category = category,
                                        symbol = pattern,
                                        sharedCandidate = sharedCandidate
                                    )
                                }
                            }
                        }
                    }
            }
        }

        val report = buildString {
            appendLine("# iOS Portability Report")
            appendLine()
            appendLine("Generated by `./gradlew iosPortabilityReport`.")
            appendLine()
            appendLine("This report is intentionally non-failing. Its job is to make Android-only dependencies visible while the Android app keeps rendering exactly as it does today.")
            appendLine()

            val sharedFindings = findings.filter { it.sharedCandidate }
            appendLine("## Shared Candidate Hotspots")
            appendLine()
            if (sharedFindings.isEmpty()) {
                appendLine("No Android/JVM-only imports or runtime usages were found in shared candidate packages.")
            } else {
                sharedFindings
                    .groupBy { it.module }
                    .toSortedMap()
                    .forEach { (moduleName, moduleFindings) ->
                        appendLine("### :$moduleName")
                        moduleFindings
                            .groupBy { it.category }
                            .toSortedMap()
                            .forEach { (category, categoryFindings) ->
                                appendLine("- $category: ${categoryFindings.size}")
                            }
                        appendLine()
                    }
            }

            appendLine("## All Main Source Hotspots")
            appendLine()
            findings
                .groupBy { it.module }
                .toSortedMap()
                .forEach { (moduleName, moduleFindings) ->
                    appendLine("### :$moduleName")
                    moduleFindings
                        .groupBy { it.category }
                        .toSortedMap()
                        .forEach { (category, categoryFindings) ->
                            appendLine("- $category: ${categoryFindings.size}")
                        }
                    appendLine()
                }

            appendLine("## Detailed Findings")
            appendLine()
            findings.sortedWith(
                compareBy<PortabilityFinding> { it.module }
                    .thenBy { !it.sharedCandidate }
                    .thenBy { it.file }
                    .thenBy { it.line }
            ).forEach { finding ->
                val marker = if (finding.sharedCandidate) "shared-candidate" else "android-boundary"
                appendLine("- `${finding.file}:${finding.line}` [$marker, ${finding.category}] `${finding.symbol}`")
            }
        }

        val reportFile = outputFile.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(report)
        logger.lifecycle("iOS portability report written to ${reportFile.relativeTo(rootDir).invariantSeparatorsPath}")
    }
}

data class PortabilityFinding(
    val module: String,
    val file: String,
    val line: Int,
    val category: String,
    val symbol: String,
    val sharedCandidate: Boolean
)
