package io.legado.app.ui.main.my.aiCorrection

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.config.prefDelegate
import io.legado.app.ui.config.translation.TranslationConfig

/**
 * AI 修正配置
 * 模型/API/密钥等复用翻译模块的 [TranslationConfig] 配置，不重复建设
 */
object AICorrectionConfig {

    var enabled: Boolean by prefDelegate(
        PreferKey.aiCorrectionEnabled,
        false
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

    /** 从翻译模块读取 API 地址 */
    val apiUrl: String get() = TranslationConfig.llmBaseUrl

    /** 从翻译模块读取 API Key */
    val apiKey: String get() = TranslationConfig.llmApiKey

    /** 从翻译模块读取模型名称 */
    val model: String get() = TranslationConfig.llmModel

    /**
     * 综合判断当前是否应该进行AI修正
     * 需要全局开关开启 AND 阅读器内开关开启 AND 已配置 API Key
     */
    val isEffectiveEnabled: Boolean
        get() = enabled && ReadBookConfig.aiCorrectionInReader && apiKey.isNotBlank()
}
