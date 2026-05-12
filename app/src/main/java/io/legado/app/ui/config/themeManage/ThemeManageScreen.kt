package io.legado.app.ui.config.themeManage

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemeImportExport
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManageScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var applyTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var exportTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var editTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var savedThemesVersion by remember { mutableIntStateOf(0) }
    val savedThemes = remember(savedThemesVersion) { ThemeImportExport.savedThemes.toList() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val target = exportTarget
            if (target != null) {
                exportTarget = null
                if (ThemeImportExport.exportSavedThemeToFile(context, target, it)) {
                    context.toastOnUi("主题导出成功")
                } else {
                    context.toastOnUi("主题导出失败")
                }
            } else if (ThemeImportExport.exportToFile(context, it)) {
                context.toastOnUi("主题导出成功")
            } else {
                context.toastOnUi("主题导出失败")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (ThemeImportExport.importFromUri(context, it)) {
                context.toastOnUi("主题导入成功，部分设置需要重启生效")
                showRestartDialog = true
            } else {
                context.toastOnUi("主题导入失败")
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "主题管理",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = "保存当前设置",
                        description = "保存当前主题配置为新主题",
                        onClick = {
                            newThemeName = ""
                            showSaveDialog = true
                        }
                    )
                    ClickableSettingItem(
                        title = "导出当前主题",
                        description = "将当前主题配置导出为JSON文件",
                        onClick = {
                            exportTarget = null
                            exportLauncher.launch("legado_theme_${System.currentTimeMillis()}.json")
                        }
                    )
                    ClickableSettingItem(
                        title = "导入主题配置",
                        description = "从JSON文件导入主题配置",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            if (savedThemes.isNotEmpty()) {
                item {
                    AppText(
                        text = "已保存的主题",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(savedThemes, key = { it.name }) { theme ->
                    SavedThemeItem(
                        theme = theme,
                        onApply = { applyTarget = theme },
                        onEdit = { editTarget = theme },
                        onExport = {
                            exportTarget = theme
                            exportLauncher.launch("${theme.name}.json")
                        },
                        onDelete = { deleteTarget = theme }
                    )
                }
            }
        }
    }

    // Restart dialog
    AppAlertDialog(
        show = showRestartDialog,
        onDismissRequest = { showRestartDialog = false },
        title = stringResource(R.string.restart_required_message),
        onConfirm = {
            showRestartDialog = false
            Handler(Looper.getMainLooper()).postDelayed({
                context.restart()
            }, 100)
        },
        confirmText = stringResource(R.string.ok),
        onDismiss = {
            showRestartDialog = false
            context.toastOnUi(R.string.restart_later_message)
        },
        dismissText = stringResource(R.string.cancel)
    )

    // Save theme dialog
    AppAlertDialog(
        show = showSaveDialog,
        onDismissRequest = { showSaveDialog = false },
        title = "保存主题",
        confirmText = "保存",
        onConfirm = {
            if (newThemeName.isNotBlank()) {
                ThemeImportExport.saveCurrentAsTheme(newThemeName)
                savedThemesVersion++
                showSaveDialog = false
            }
        },
        dismissText = "取消",
        onDismiss = { showSaveDialog = false },
        content = {
            AppTextField(
                value = newThemeName,
                onValueChange = { newThemeName = it },
                placeholder = { AppText(text = "请输入主题名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )

    // Apply theme dialog
    AppAlertDialog(
        show = applyTarget != null,
        onDismissRequest = { applyTarget = null },
        title = "应用主题",
        confirmText = "应用",
        onConfirm = {
            applyTarget?.let { theme ->
                ThemeImportExport.applySavedTheme(theme)
                showRestartDialog = true
            }
            applyTarget = null
        },
        dismissText = "取消",
        onDismiss = { applyTarget = null },
        text = "确定应用主题「${applyTarget?.name}」？应用后需要重启才能完全生效。"
    )

    // Delete theme dialog
    AppAlertDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleteTarget = null },
        title = "删除主题",
        confirmText = "删除",
        onConfirm = {
            deleteTarget?.let { theme ->
                ThemeImportExport.deleteSavedTheme(theme)
                savedThemesVersion++
            }
            deleteTarget = null
        },
        dismissText = "取消",
        onDismiss = { deleteTarget = null },
        text = "确定删除主题「${deleteTarget?.name}」？此操作不可恢复。"
    )

    // Edit theme sheet
    EditThemeSheet(
        show = editTarget != null,
        themeData = editTarget?.data,
        themeName = editTarget?.name ?: "",
        onDismissRequest = { editTarget = null },
        onSave = { newName, newData ->
            editTarget?.let { old ->
                ThemeImportExport.deleteSavedTheme(old)
            }
            ThemeImportExport.saveCurrentAsTheme(newName, newData)
            savedThemesVersion++
            editTarget = null
        }
    )
}

@Composable
private fun SavedThemeItem(
    theme: SavedTheme,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        onClick = onApply,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val previewColor = if (theme.data.themeColor != 0) {
                Color(theme.data.themeColor)
            } else if (theme.data.cPrimary != 0) {
                Color(theme.data.cPrimary)
            } else {
                MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                AppText(
                    text = theme.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                val features = mutableListOf<String>()
                features.add(
                    when (theme.data.appTheme) {
                        "0" -> "动态取色"
                        "12" -> "自定义颜色"
                        else -> "预设主题"
                    }
                )
                if (theme.data.enableBlur) features.add("模糊效果")
                if (theme.data.useFloatingBottomBar) features.add("浮动底栏")
                AppText(
                    text = features.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "导出",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
