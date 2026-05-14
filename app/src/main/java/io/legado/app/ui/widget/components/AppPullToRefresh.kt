package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as miuixRememberPullToRefreshState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            val state = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
                state = state,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = state,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
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
