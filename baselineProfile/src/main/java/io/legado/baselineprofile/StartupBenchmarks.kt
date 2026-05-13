package io.legado.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing [CompilationMode.None], which represents the app with no Baseline
 * Profiles optimizations, and [CompilationMode.Partial], which uses Baseline Profiles.
 *
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 *
 * For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark)
 * and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() =
        benchmark(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() =
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) {
        // The application id for the running build variant is read from the instrumentation arguments.
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "io.legado.app"

        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 3,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()

                // 等待书架列表加载完成
                // 我们在 BookshelfScreen 中添加了 testTag("bookshelf_list")
                device.wait(Until.hasObject(By.res(packageName, "bookshelf_list")), 10000)

                // 模拟交互：在书架上进行滑动，以确保 Profile 捕获到列表渲染和图片加载的路径
                val bookshelfList = device.findObject(By.res(packageName, "bookshelf_list"))
                bookshelfList?.apply {
                    setGestureMargin(device.displayWidth / 10)
                    fling(Direction.DOWN)
                    device.waitForIdle()
                }
            }
        )
    }
}