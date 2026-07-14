import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    val xcf = XCFramework("LastChatUI")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "LastChatUI"
            isStatic = true
            binaryOption("bundleId", "lastchat.rikkafork.cocolal.shared.ui")
            export(project(":ai"))
            export(project(":common"))
            export(project(":search"))
            export(project(":tts"))
            export(project(":shared"))
            xcf.add(this)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            api(project(":ai"))
            api(project(":common"))
            api(project(":search"))
            api(project(":tts"))
            api(project(":shared"))
            implementation(project(":ui-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "me.rerere.lastchat.ios.generated.resources"
}
