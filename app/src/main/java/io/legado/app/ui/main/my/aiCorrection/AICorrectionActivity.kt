package io.legado.app.ui.main.my.aiCorrection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.book.AIContentCorrector
import io.legado.app.model.ReadBook
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch

class AICorrectionActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
        var showRulesDialog by remember { mutableStateOf(false) }
        var tempRules by remember { mutableStateOf(AICorrectionConfig.rules) }
        var isTesting by remember { mutableStateOf(false) }

        val hasTranslationConfig = TranslationConfig.llmBaseUrl.isNotBlank() &&
                TranslationConfig.llmApiKey.isNotBlank()

        AppScaffold(
            topBar = {
                GlassMediumFlexibleTopAppBar(
                    title = stringResource(R.string.ai_correction),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = { finish() }) {
                            androidx.compose.material3.Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        ) { padding ->
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = adaptiveContentPadding(
                    top = padding.calculateTopPadding(),
                    bottom = 120.dp
                )
            ) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.ai_correction)) {
                        SwitchSettingItem(
                            title = stringResource(R.string.ai_correction_enable),
                            description = stringResource(R.string.ai_correction_enable_desc),
                            checked = AICorrectionConfig.enabled,
                            onCheckedChange = { checked -> AICorrectionConfig.enabled = checked }
                        )

                        // 模型配置 — 复用翻译模块
                        ClickableSettingItem(
                            title = "模型与API配置",
                            description = if (hasTranslationConfig)
                                "${TranslationConfig.llmModel.ifBlank { "（使用默认模型）"}} · ${TranslationConfig.llmBaseUrl.take(50)}"
                            else
                                "未配置（请前往 设置→翻译设置）",
                            onClick = { /* 跳转到翻译配置？或 toast 提示 */ }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_rules),
                            description = stringResource(R.string.ai_correction_rules_desc),
                            onClick = { showRulesDialog = true }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_test),
                            description = stringResource(R.string.ai_correction_test_desc),
                            onClick = {
                                if (isTesting || AICorrectionConfig.apiKey.isBlank()) {
                                    if (AICorrectionConfig.apiKey.isBlank()) {
                                        context.toastOnUi(context.getString(R.string.ai_correction_api_key_empty))
                                    }
                                } else {
                                    isTesting = true
                                    scope.launch {
                                        val testContent = "「你好。」他说。\n\n『你好。』她回答。\n\nhttps://example.com"
                                        val result = AIContentCorrector.correct(testContent, "测试章节", "AICorrectionActivity")
                                        isTesting = false
                                        if (result.isNotBlank() && result != testContent) {
                                            context.toastOnUi(context.getString(R.string.ai_correction_test_success))
                                        } else {
                                            context.toastOnUi(context.getString(R.string.ai_correction_test_failed))
                                        }
                                    }
                                }
                            }
                        )

                        ClickableSettingItem(
                            title = "查看日志",
                            description = "查看AI修正的详细日志",
                            onClick = {
                                showDialogFragment<AppLogDialog>()
                            }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_clear_cache),
                            description = stringResource(R.string.ai_correction_clear_cache_desc),
                            onClick = {
                                ReadBook.clearCorrectionCache()
                                context.toastOnUi(context.getString(R.string.ai_correction_cleared))
                            }
                        )
                    }
                }
            }
        }

        if (showRulesDialog) {
            AppAlertDialog(
                show = showRulesDialog,
                onDismissRequest = { showRulesDialog = false },
                title = stringResource(R.string.ai_correction_rules),
                content = {
                    AppTextField(
                        value = tempRules,
                        onValueChange = { tempRules = it },
                        label = stringResource(R.string.ai_correction_rules_hint),
                        backgroundColor = io.legado.app.ui.theme.LegadoTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    AICorrectionConfig.rules = tempRules
                    showRulesDialog = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showRulesDialog = false }
            )
        }
    }
}
