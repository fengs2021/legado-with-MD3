package io.legado.app.ui.main.my.aiCorrection

import io.legado.app.help.config.ReadConfig
import io.legado.app.help.config.prefDelegate
import io.legado.app.constant.PreferKey
import splitties.init.appCtx

/**
 * AI 修正配置
 * 放在 ReadConfig 里持久化
 */
object AICorrectionConfig {

    var aiModel: String by prefDelegate(
        PreferKey.aiCorrectionModel,
        "MiniMax-Text-01"
    )

    var apiKey: String by prefDelegate(
        PreferKey.aiCorrectionApiKey,
        ""
    )

    var rules: String by prefDelegate(
        PreferKey.aiCorrectionRules,
        ""
    )

    var enabled: Boolean by prefDelegate(
        PreferKey.aiCorrectionEnabled,
        false
    )
}
