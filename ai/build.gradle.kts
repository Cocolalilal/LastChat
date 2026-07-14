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
        }
        commonMain {
            kotlin.srcDir("src/main/java")
            dependencies {
                api(project(":common"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest {
            kotlin.srcDir("src/test/java")
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

android {
    namespace = "me.rerere.ai"
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
