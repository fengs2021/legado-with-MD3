package io.legado.app.help.book

import io.legado.app.ui.main.my.aiCorrection.AICorrectionConfig
import io.legado.app.constant.PreferKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.MediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 正文修正器
 */
object AIContentCorrector {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private const val API_URL = "https://api.minimaxi.com/v1/text/chatcompletion_v2"

    /**
     * 修正正文内容
     * @param content 原始正文
     * @param chapterTitle 章节标题（用于对话提示）
     * @return 修正后的正文
     */
    suspend fun correct(content: String, chapterTitle: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = AICorrectionConfig.apiKey
        val model = AICorrectionConfig.aiModel.ifBlank { "MiniMax-Text-01" }
        val rules = AICorrectionConfig.rules

        if (apiKey.isBlank()) {
            return@withContext content
        }

        // 构建 prompt
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
            .toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext content
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices") ?: return@withContext content
            if (choices.length() == 0) return@withContext content
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: return@withContext content
            val result = message.optString("content") ?: return@withContext content
            parseResult(result)
        } catch (e: Exception) {
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
        // 尝试从【正文开始】和【正文结束】之间提取内容
        val startMark = "【正文开始】"
        val endMark = "【正文结束】"
        val startIdx = raw.indexOf(startMark)
        val endIdx = raw.indexOf(endMark)

        if (startIdx >= 0 && endIdx >= 0) {
            return raw.substring(startIdx + startMark.length, endIdx).trim()
        }

        // 如果没找到标记，尝试用 ``` 包裹的代码块
        val codeBlockPattern = Regex("```[\\s\\S]*?```")
        val matches = codeBlockPattern.findAll(raw).toList()
        if (matches.any()) {
            // 取最后一个代码块（通常是最终结果）
            val lastBlock = matches.last().value
            return lastBlock.removeSurrounding("```").trim()
        }

        // 降级：直接返回原始内容
        return raw.trim()
    }
}
