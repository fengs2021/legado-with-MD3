package io.legado.app.ui.main.my.aiCorrection

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate
import splitties.init.appCtx

/**
 * AI 修正配置
 */
object AICorrectionConfig {

    var enabled: Boolean by prefDelegate(
        PreferKey.aiCorrectionEnabled,
        false
    )

    var provider: String by prefDelegate(
        PreferKey.aiCorrectionProvider,
        "minimax"
    )

    var model: String by prefDelegate(
        PreferKey.aiCorrectionModel,
        ""
    )

    var apiKey: String by prefDelegate(
        PreferKey.aiCorrectionApiKey,
        ""
    )

    var rules: String by prefDelegate(
        PreferKey.aiCorrectionRules,
        "你是一位严谨的小说审稿编辑。请对以下小说正文做三件事：\n\n1. 删除垃圾内容：删除所有网址、广告、章节提示（如"本小章未完——请点击下一页"）、作者留言、求票求收藏等非正文内容。\n\n2. 修正对话标点：所有对话使用标准双引号""替换其他引号（如「」、『』）。对话结束后句末标点放在后引号内。示例：\n  「那就还有机会！」→ "那就还有机会！"\n  他问：『你去哪？』→ 他问："你去哪？"\n\n3. 修正明显错别字和漏字：不改变作者文风，不拆分合并段落，不修改分段逻辑。对于无法确定的错字保留原样。\n\n只输出修正后的正文，不要任何解释。"
    )

    val providerNames = mapOf(
        "minimax" to "MiniMax",
        "kimi" to "月之暗面 (Kimi)",
        "deepseek" to "DeepSeek",
        "qwen" to "通义千问 (Qwen)",
        "openai" to "OpenAI"
    )
}
