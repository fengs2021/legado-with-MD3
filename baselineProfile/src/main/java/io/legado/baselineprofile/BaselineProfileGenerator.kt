package io.legado.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
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
 * This test class generates a basic startup baseline profile for the target package.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "io.legado.app"

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true
        ) {
            // 1. 启动应用
            pressHome()
            startActivityAndWait()

            // 2. 等待书架加载 (我们在 BookshelfScreen 中添加了 ReportDrawnWhen)
            // 等待书架列表 testTag 出现
            device.wait(Until.hasObject(By.res(packageName, "bookshelf_list")), 10000)

            // 3. 模拟滑动书架 (优化列表渲染性能)
            val bookshelf = device.findObject(By.res(packageName, "bookshelf_list"))
            bookshelf?.apply {
                setGestureMargin(device.displayWidth / 10)
                fling(Direction.DOWN)
                device.waitForIdle()
                fling(Direction.UP)
                device.waitForIdle()
            }

            // 4. 遍历主要 Tab (优化 Compose 导航和各页面初始化)
            val tabs = listOf("nav_explore", "nav_rss", "nav_my", "nav_bookshelf")
            for (tabTag in tabs) {
                val tab = device.findObject(By.res(packageName, tabTag))
                if (tab != null) {
                    tab.click()
                    device.waitForIdle()
                    // 稍微等待页面加载
                    Thread.sleep(500) 
                }
            }

        }
    }
}