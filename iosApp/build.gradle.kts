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
        iosMain.dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "me.rerere.lastchat.ios.generated.resources"
}

val webUiDir = rootProject.file("web-ui")
val webUiBuildDir = webUiDir.resolve("build/client")
val iosWebUiDest = layout.projectDirectory.dir("xcode/LastChatIOS/webui")
val skipIosWebUiBuild = providers.gradleProperty("lastchat.ios.webui.skip").orNull == "true"

val buildWebUiForIos by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the React web-ui client that Xcode bundles into LastChatIOS/webui."
    workingDir = webUiDir
    inputs.dir(webUiDir.resolve("app"))
    inputs.dir(webUiDir.resolve("public"))
    inputs.file(webUiDir.resolve("package.json"))
    inputs.file(webUiDir.resolve("react-router.config.ts"))
    inputs.file(webUiDir.resolve("tsconfig.json"))
    inputs.file(webUiDir.resolve("vite.config.ts"))
    val packageLock = webUiDir.resolve("package-lock.json")
    if (packageLock.exists()) {
        inputs.file(packageLock)
    }
    outputs.dir(webUiBuildDir)
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    commandLine(if (windows) listOf("cmd", "/c", "npm", "run", "build") else listOf("npm", "run", "build"))
    onlyIf {
        !skipIosWebUiBuild && webUiDir.resolve("package.json").exists()
    }
}

val prepareIosWebUi by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies web-ui/build/client into iosApp/xcode/LastChatIOS/webui for the Xcode resource folder."
    dependsOn(buildWebUiForIos)
    from(webUiBuildDir)
    into(iosWebUiDest)
    includeEmptyDirs = false
    exclude(".gitkeep")
    onlyIf { webUiBuildDir.resolve("index.html").exists() }
    doLast {
        iosWebUiDest.asFile.resolve(".gitkeep").takeIf { !it.exists() }?.writeText("")
    }
}

tasks.matching { it.name.contains("embedAndSignAppleFrameworkForXcode") }.configureEach {
    dependsOn(prepareIosWebUi)
}

tasks.register("iosWebUi") {
    group = "build"
    description = "Builds the React SPA and copies it into the Xcode webui resource folder."
    dependsOn(prepareIosWebUi)
}
