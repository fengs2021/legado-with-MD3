package io.legado.app.help.book

import io.legado.app.constant.AppLog
import io.legado.app.ui.main.my.aiCorrection.AICorrectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 正文修正器
 */
object AIContentCorrector {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private const val API_URL_MINIMAX = "https://api.minimaxi.com/v1/text/chatcompletion_v2"
    private const val API_URL_KIMI = "https://api.moonshot.cn/v1/chat/completions"
    private const val API_URL_KIMI_CODE = "https://api.kimi.com/coding/v1/chat/completions"
    private const val API_URL_DEEPSEEK = "https://api.deepseek.com/chat/completions"
    private const val API_URL_QWEN = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val API_URL_OPENAI = "https://api.openai.com/v1/chat/completions"

    /**
     * 修正正文内容
     * @param content 原始正文
     * @param chapterTitle 章节标题（用于对话提示）
     * @return 修正后的正文
     */
    suspend fun correct(content: String, chapterTitle: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = AICorrectionConfig.apiKey
        val provider = AICorrectionConfig.provider
        val model = AICorrectionConfig.getEffectiveModel()
        val rules = AICorrectionConfig.rules
        val apiUrl = AICorrectionConfig.getEffectiveApiUrl()

        if (apiKey.isBlank() || apiUrl.isBlank()) {
            return@withContext content
        }

        val prompt = buildPrompt(content, chapterTitle, rules)

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
            put("max_tokens", 8192)
            put("temperature", 0.3)
        }

        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: run {
                AppLog.put("AI修正失败: 空响应")
                return@withContext content
            }
            AppLog.put("AI修正响应: $body")
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices") ?: run {
                AppLog.put("AI修正失败: 无choices")
                return@withContext content
            }
            if (choices.length() == 0) return@withContext content
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: run {
                    AppLog.put("AI修正失败: 无message")
                    return@withContext content
                }
            val result = message.optString("content") ?: run {
                AppLog.put("AI修正失败: 无content")
                return@withContext content
            }
            AppLog.put("AI修正成功")
            parseResult(result)
        } catch (e: Exception) {
            AppLog.put("AI修正异常: ${e.localizedMessage}")
            e.printStackTrace()
            content
        }
    }

    private fun buildPrompt(content: String, chapterTitle: String, rules: String): String {
        val sb = StringBuilder()
        sb.append("你是一个专业的小说文本修正助手。\n\n")

        if (chapterTitle.isNotBlank()) {
            sb.append("章节：$chapterTitle\n")
        }

        sb.append("请修正以下正文中的问题（错别字、标点、格式等），并按照要求的规则处理。\n\n")

        if (rules.isNotBlank()) {
            sb.append("修正规则：\n$rules\n\n")
        }

        sb.append("待修正的正文：\n")
        sb.append(content)
        sb.append("\n\n请直接返回修正后的正文，不需要任何解释。修正后的正文请用以下格式包裹：\n")
        sb.append("【正文开始】\n")
        sb.append("修正后的内容...\n")
        sb.append("【正文结束】")

        return sb.toString()
    }

    private fun parseResult(raw: String): String {
        val startMark = "【正文开始】"
        val endMark = "【正文结束】"
        val startIdx = raw.indexOf(startMark)
        val endIdx = raw.indexOf(endMark)

        if (startIdx >= 0 && endIdx >= 0) {
            return raw.substring(startIdx + startMark.length, endIdx).trim()
        }

        val codeBlockPattern = Regex("```[\\s\\S]*?```")
        val matches = codeBlockPattern.findAll(raw).toList()
        if (matches.any()) {
            val lastBlock = matches.last().value
            return lastBlock.removeSurrounding("```").trim()
        }

        return raw.trim()
    }
}
