package me.rerere.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The application id for the running build variant is read from the instrumentation arguments.
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            // Memory Center graph-canvas journey (§10.2 / P6). The custom force-directed Canvas
            // (MemoryGraphLayout / MemoryForceLayout / MemoryGraphBuilder / MemoryGraphTab) is the
            // heaviest first-render surface added in P4b, so we want its classes in the profile.
            // Best-effort and fully guarded: the graph tab is only reachable when the memory system
            // is enabled, so a failure here must never abort startup-profile generation.
            exerciseMemoryGraphCanvas()
        }
    }

    /**
     * Best-effort navigation into Memory Center → Graph tab so the graph-canvas classes are AOT
     * captured. Wrapped in [runCatching]: if the memory feature is off, the labels differ on a given
     * build, or the surface is unreachable, we silently fall back to the startup-only profile.
     */
    private fun MacrobenchmarkScope.exerciseMemoryGraphCanvas() {
        runCatching {
            // The "Graph" primary tab inside Memory Center (see MemoryCenterPage.MemoryTab.GRAPH).
            val graphTab = device.wait(Until.findObject(By.text("Graph")), 3_000) ?: return@runCatching
            graphTab.click()
            device.wait(Until.hasObject(By.text("Overview")), 2_000)
            // Let the off-thread force layout settle and freeze, then pan/zoom the pure graphicsLayer
            // transform a couple of times to exercise the gesture/render path.
            repeat(2) {
                device.waitForIdle(1_000)
                val bounds = device.displayHeight
                device.swipe(device.displayWidth / 2, bounds / 3, device.displayWidth / 2, bounds * 2 / 3, 8)
            }
        }
    }
}
