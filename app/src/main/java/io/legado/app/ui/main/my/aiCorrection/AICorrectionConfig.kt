package io.legado.app.ui.main.my.aiCorrection

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
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

    var model: String
        get() {
            val key = PreferKey.aiCorrectionModelPrefix + provider
            return appCtx.getPrefString(key, "") ?: ""
        }
        set(value) {
            val key = PreferKey.aiCorrectionModelPrefix + provider
            appCtx.putPrefString(key, value)
        }

    var apiKey: String by prefDelegate(
        PreferKey.aiCorrectionApiKey,
        ""
    )

    var customApiUrl: String by prefDelegate(
        PreferKey.aiCorrectionCustomApiUrl,
        ""
    )

    var customModel: String by prefDelegate(
        PreferKey.aiCorrectionCustomModel,
        ""
    )

    var rules: String by prefDelegate(
        PreferKey.aiCorrectionRules,
        """
你是一位严谨的小说审稿编辑。请对以下小说正文做三件事：

1. 删除垃圾内容：删除所有网址、广告、章节提示（如「本小章未完——请点击下一页」）、作者留言、求票求收藏等非正文内容。

2. 修正对话标点：所有对话使用标准双引号替换其他引号（如「」、「『』」）。对话结束后句末标点放在后引号内。

3. 修正明显错别字和漏字：不改变作者文风，不拆分合并段落，不修改分段逻辑。对于无法确定的错字保留原样。

只输出修正后的正文，不要任何解释。
        """
    )

    val providerNames = mapOf(
        "minimax" to "MiniMax",
        "kimi" to "Moonshot (Kimi 开放平台)",
        "kimi-code" to "Kimi Code (kimi.com/code)",
        "deepseek" to "DeepSeek",
        "qwen" to "通义千问 (Qwen)",
        "openai" to "OpenAI",
        "custom" to "自定义 (Custom)"
    )

    val isCustom: Boolean get() = provider == "custom"

    fun getEffectiveApiUrl(): String = when (provider) {
        "custom" -> customApiUrl.ifBlank { "" }
        else -> getApiUrl(provider)
    }

    fun getEffectiveModel(): String = when (provider) {
        "custom" -> customModel.ifBlank { "" }
        else -> model.ifBlank { getDefaultModel(provider) }
    }

    private fun getApiUrl(provider: String): String = when (provider) {
        "kimi" -> API_URL_KIMI
        "kimi-code" -> API_URL_KIMI_CODE
        "deepseek" -> API_URL_DEEPSEEK
        "qwen" -> API_URL_QWEN
        "openai" -> API_URL_OPENAI
        else -> API_URL_MINIMAX
    }

    private fun getDefaultModel(provider: String): String = when (provider) {
        "kimi" -> "moonshot-v1-8k"
        "kimi-code" -> "kimi-for-coding"
        "deepseek" -> "deepseek-chat"
        "qwen" -> "qwen-turbo"
        "openai" -> "gpt-4o-mini"
        else -> "MiniMax-Text-01"
    }

    private const val API_URL_MINIMAX = "https://api.minimaxi.com/v1/text/chatcompletion_v2"
    private const val API_URL_KIMI = "https://api.moonshot.cn/v1/chat/completions"
    private const val API_URL_KIMI_CODE = "https://api.kimi.com/coding/v1/chat/completions"

    /**
     * 综合判断当前是否应该进行AI修正
     * 需要全局开关开启 AND 阅读器内开关开启
     */
    val isEffectiveEnabled: Boolean
        get() = enabled && ReadBookConfig.aiCorrectionInReader

    private const val API_URL_DEEPSEEK = "https://api.deepseek.com/chat/completions"
    private const val API_URL_QWEN = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val API_URL_OPENAI = "https://api.openai.com/v1/chat/completions"
}
