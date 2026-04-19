package io.legado.app.ui.main.my.aiCorrection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.book.AIContentCorrector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingitem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingitem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch

class AICorrectionActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
        var showApiKeyDialog by remember { mutableStateOf(false) }
        var showRulesDialog by remember { mutableStateOf(false) }
        var tempApiKey by remember { mutableStateOf(AICorrectionConfig.apiKey) }
        var tempRules by remember { mutableStateOf(AICorrectionConfig.rules) }
        var isTesting by remember { mutableStateOf(false) }

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
                            onCheckedChange = { AICorrectionConfig.enabled = it }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_model),
                            description = AICorrectionConfig.aiModel.ifBlank { stringResource(R.string.ai_correction_default_model) },
                            onClick = { showApiKeyDialog = true }
                        )

                        ClickableSettingItem(
                            title = stringResource(R.string.ai_correction_rules),
                            description = AICorrectionConfig.rules.ifBlank { stringResource(R.string.ai_correction_rules_desc) },
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
                    }
                }
            }
        }

        if (showApiKeyDialog) {
            AppAlertDialog(
                show = showApiKeyDialog,
                onDismissRequest = { showApiKeyDialog = false },
                title = stringResource(R.string.ai_correction_model),
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
