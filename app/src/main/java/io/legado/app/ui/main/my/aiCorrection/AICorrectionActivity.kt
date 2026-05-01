package io.legado.app.ui.main.my.aiCorrection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.book.AIContentCorrector
import io.legado.app.constant.AppLog
import io.legado.app.model.ReadBook
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.theme.LegadoTheme
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
        var showApiKeyDialog by remember { mutableStateOf(false) }
        var showProviderDialog by remember { mutableStateOf(false) }
        var showModelDialog by remember { mutableStateOf(false) }
        var showCustomUrlDialog by remember { mutableStateOf(false) }
        var showRulesDialog by remember { mutableStateOf(false) }
        var tempApiKey by remember { mutableStateOf(AICorrectionConfig.apiKey) }
        var tempModel by remember { mutableStateOf(AICorrectionConfig.model) }
        var tempCustomUrl by remember { mutableStateOf(AICorrectionConfig.customApiUrl) }
        var tempCustomModel by remember { mutableStateOf(AICorrectionConfig.customModel) }
        var tempRules by remember { mutableStateOf(AICorrectionConfig.rules) }
        var isTesting by remember { mutableStateOf(false) }

        val modelDesc = if (AICorrectionConfig.isCustom) {
            (AICorrectionConfig.customModel.ifBlank { "（未设置）" })
        } else {
            AICorrectionConfig.model.ifBlank {
                when (AICorrectionConfig.provider) {
                    "kimi" -> "moonshot-v1-8k"
                    "kimi-code" -> "kimi-for-coding"
                    "deepseek" -> "deepseek-chat"
                    "qwen" -> "qwen-turbo"
                    "openai" -> "gpt-4o-mini"
                    else -> "MiniMax-Text-01"
                }
            }
        }

        val customUrlDesc = AICorrectionConfig.customApiUrl.ifBlank { "（未设置）" }

        AppScaffold(
            topBar = {
                GlassMediumFlexibleTopAppBar(
                    title = stringResource(R.string.ai_correction),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
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

                        ClickableSettingItem(
                            title = "AI 供应商",
                            description = AICorrectionConfig.providerNames[AICorrectionConfig.provider]
                                ?: AICorrectionConfig.provider,
                            onClick = { showProviderDialog = true }
                        )

                        if (AICorrectionConfig.isCustom) {
                            ClickableSettingItem(
                                title = "API 接口地址",
                                description = customUrlDesc,
                                onClick = { showCustomUrlDialog = true }
                            )
                        }

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_model),
                            description = modelDesc,
                            onClick = { showModelDialog = true }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_api_key),
                            description = if (AICorrectionConfig.apiKey.isNotBlank()) "******"
                                          else stringResource(R.string.ai_correction_api_key_empty),
                            onClick = { showApiKeyDialog = true }
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
                                        val result = AIContentCorrector.correct(testContent, "测试章节")
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

        if (showApiKeyDialog) {
            AppAlertDialog(
                show = showApiKeyDialog,
                onDismissRequest = { showApiKeyDialog = false },
                title = stringResource(R.string.ai_correction_api_key),
                content = {
                    AppTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = "API Key",
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    AICorrectionConfig.apiKey = tempApiKey
                    showApiKeyDialog = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showApiKeyDialog = false }
            )
        }

        if (showProviderDialog) {
            AppAlertDialog(
                show = showProviderDialog,
                onDismissRequest = { showProviderDialog = false },
                title = "选择 AI 供应商",
                content = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        AICorrectionConfig.providerNames.forEach { (id, name) ->
                            item {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            AICorrectionConfig.provider = id
                                            showProviderDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = AICorrectionConfig.provider == id,
                                        onClick = {
                                            AICorrectionConfig.provider = id
                                            showProviderDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                    androidx.compose.material3.Text(name)
                                }
                            }
                        }
                    }
                },
                confirmText = stringResource(R.string.cancel),
                onConfirm = { showProviderDialog = false }
            )
        }

        if (showModelDialog) {
            AppAlertDialog(
                show = showModelDialog,
                onDismissRequest = { showModelDialog = false },
                title = stringResource(R.string.ai_correction_model),
                content = {
                    AppTextField(
                        value = if (AICorrectionConfig.isCustom) tempCustomModel else tempModel,
                        onValueChange = {
                            if (AICorrectionConfig.isCustom) {
                                tempCustomModel = it
                            } else {
                                tempModel = it
                            }
                        },
                        label = "模型名称",
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    if (AICorrectionConfig.isCustom) {
                        AICorrectionConfig.customModel = tempCustomModel
                    } else {
                        AICorrectionConfig.model = tempModel
                    }
                    showModelDialog = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showModelDialog = false }
            )
        }

        if (showCustomUrlDialog) {
            AppAlertDialog(
                show = showCustomUrlDialog,
                onDismissRequest = { showCustomUrlDialog = false },
                title = "自定义 API 接口地址",
                content = {
                    AppTextField(
                        value = tempCustomUrl,
                        onValueChange = { tempCustomUrl = it },
                        label = "API URL（如 https://api.example.com/v1/chat/completions）",
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    AICorrectionConfig.customApiUrl = tempCustomUrl
                    showCustomUrlDialog = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showCustomUrlDialog = false }
            )
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
                        backgroundColor = LegadoTheme.colorScheme.surface,
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
