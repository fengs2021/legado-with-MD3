package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as miuixRememberPullToRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        if (enabled) {
            MiuixPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
                pullToRefreshState = miuixRememberPullToRefreshState(),
                refreshTexts = listOf(
                    "下拉刷新",
                    "松开刷新",
                    "正在刷新",
                    "刷新成功"
                )
            ) {
                content()
            }
        } else {
            Box(modifier) {
                content()
            }
        }
    } else {
        if (enabled) {
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
                state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
            ) {
                content()
            }
        } else {
            Box(modifier) {
                content()
            }
        }
    }
}
