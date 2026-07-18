import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val generatedComposeResources = layout.buildDirectory.dir("generated/composeResources")
val prepareSharedResources by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/res/font/google_sans_flex.ttf")) {
        into("font")
    }
    from(rootProject.file("app/src/main/res/mipmap-xxxhdpi/ic_launcher_lastchat_foreground.png")) {
        into("drawable")
    }
    into(generatedComposeResources)
}

val generatedPresetSources = layout.buildDirectory.dir("generated/presetSources")
val presetSourceFiles = linkedMapOf(
    "ocean" to rootProject.file("app/src/main/java/me/rerere/rikkahub/ui/theme/presets/OceanTheme.kt"),
    "spring" to rootProject.file("app/src/main/java/me/rerere/rikkahub/ui/theme/presets/SpringTheme.kt"),
    "autumn" to rootProject.file("app/src/main/java/me/rerere/rikkahub/ui/theme/presets/AutumnTheme.kt"),
    "black" to rootProject.file("app/src/main/java/me/rerere/rikkahub/ui/theme/presets/BlackTheme.kt"),
)
val generateSharedPresetSchemes by tasks.registering {
    inputs.files(presetSourceFiles.values)
    val outputFile = generatedPresetSources.map {
        it.file("me/rerere/rikkahub/ui/theme/GeneratedPresetColorSchemes.kt")
    }
    outputs.file(outputFile)
    doLast {
        val colorPattern = Regex("private val (\\w+) = Color\\(0x([0-9A-Fa-f]+)\\)")
        fun schemeArguments(source: String, scheme: String, colors: Map<String, String>): String {
            val block = Regex(
                "private val ${scheme}Scheme = ${scheme}ColorScheme\\(([\\s\\S]*?)\\n\\)",
            ).find(source)?.groupValues?.get(1)
                ?: error("Unable to find ${scheme}Scheme")
            return Regex("(\\w+)\\s*=\\s*(\\w+)").findAll(block).joinToString(",\n") { match ->
                val property = match.groupValues[1]
                val variable = match.groupValues[2]
                val value = colors[variable] ?: error("Missing color $variable")
                "        $property = Color(0x$value)"
            }
        }
        val definitions = presetSourceFiles.map { (id, file) ->
            val source = file.readText()
            val colors = colorPattern.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
            val className = id.replaceFirstChar { it.uppercase() }
            val light = schemeArguments(source, "light", colors)
            val dark = schemeArguments(source, "dark", colors)
            """
            private val ${className}Light = lightColorScheme(
$light,
            )
            private val ${className}Dark = darkColorScheme(
$dark,
            )
            """.trimIndent()
        }.joinToString("\n\n")
        val branches = presetSourceFiles.keys.joinToString("\n") { id ->
            val className = id.replaceFirstChar { it.uppercase() }
            "        \"$id\" -> if (dark) ${className}Dark else ${className}Light"
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package me.rerere.rikkahub.ui.theme

            import androidx.compose.material3.ColorScheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.ui.graphics.Color

            $definitions

            fun presetColorScheme(id: String, dark: Boolean): ColorScheme = when (id) {
                "seafoam_mint" -> seafoamMintColorScheme(dark)
                "sakura" -> sakuraColorScheme(dark)
$branches
                else -> seafoamMintColorScheme(dark)
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets.commonMain.dependencies {
        implementation(project(":shared"))
        api(compose.runtime)
        api(compose.foundation)
        api(compose.ui)
        api(compose.material3)
        api(compose.materialIconsExtended)
        implementation(compose.components.resources)
        implementation(libs.kotlinx.datetime)
    }
    sourceSets.commonMain {
        kotlin.srcDir(generatedPresetSources)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

compose.resources {
    packageOfResClass = "me.rerere.rikkahub.ui.core.generated.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = generatedComposeResources,
    )
}

tasks.matching {
    it.name != "prepareSharedResources" && (
        it.name.contains("Resource", ignoreCase = true) ||
            it.name.contains("ComposeResources", ignoreCase = true)
    )
}.configureEach {
    dependsOn(prepareSharedResources)
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn(generateSharedPresetSchemes) }

android {
    namespace = "me.rerere.rikkahub.ui.core"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
