import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
        commonMain {
            kotlin.srcDir("src/main/java")
            kotlin.exclude("me/rerere/tts/provider/android/**")
            kotlin.exclude("me/rerere/tts/provider/providers/android/**")
            kotlin.exclude("me/rerere/tts/controller/AudioPlayer.kt")
            dependencies {
                api(project(":common"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain {
            kotlin.srcDir("src/main/java")
            kotlin.include("me/rerere/tts/provider/TtsDispatcher.android.kt")
            kotlin.include("me/rerere/tts/provider/android/**")
            kotlin.include("me/rerere/tts/provider/providers/android/**")
            kotlin.include("me/rerere/tts/controller/AudioPlayer.kt")
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.ui)
                implementation(libs.androidx.media3.common)
            }
        }
        androidUnitTest {
            kotlin.srcDir("src/test/java")
            dependencies { implementation(libs.junit) }
        }
    }
}

android {
    namespace = "me.rerere.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("main").java.setSrcDirs(emptyList<String>())
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
