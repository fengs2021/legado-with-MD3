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

    /** 修正失败时抛出的异常 */
    class CorrectionException(msg: String) : Exception(msg)

    /** 修正结果校验失败时抛出的异常 */
    class ValidationException(msg: String) : Exception(msg)

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

    /** 修正结果最大允许倍数（相对原文长度），防止 AI 无限膨胀 */
    private const val MAX_RESULT_LENGTH_MULTIPLIER = 3.0

    /** 修正结果最小有效长度 */
    private const val MIN_VALID_LENGTH = 10

    /**
     * 修正正文内容
     * @param content 原始正文
     * @param chapterTitle 章节标题（用于对话提示）
     * @param source 调用来源标识，用于日志区分不同触发点
     * @return 修正后的正文
     */
    suspend fun correct(content: String, chapterTitle: String = "", source: String = ""): String = withContext(Dispatchers.IO) {
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
                AppLog.put("[${chapterTitle}] AI修正失败: 空响应")
                throw CorrectionException("空响应")
            }
            AppLog.put("[${chapterTitle}] AI修正响应: ${body.take(200)}")
            // 检测API错误（如overloaded、timeout等）
            if (body.contains("overloaded_error") || body.contains("timeout") || body.contains("rate_limit")) {
                val errMsg = if (body.contains("overloaded_error")) "API服务器过载(overloaded)" 
                    else if (body.contains("timeout")) "API超时(timeout)"
                    else "API限流(rate_limit)"
                AppLog.put("[${chapterTitle}] AI修正失败: $errMsg")
                throw CorrectionException(errMsg)
            }
            val json = JSONObject(body)
            // 检查是否有 API 错误
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val errorMsg = errorObj.optString("message", "")
                    .ifEmpty { errorObj.optString("type", "未知错误") }
                AppLog.put("[${chapterTitle}] AI修正失败: API错误 - $errorMsg")
                AppLog.put("[${chapterTitle}] AI修正完整响应: $body")
                throw CorrectionException("API错误: $errorMsg")
            }
            val choices = json.optJSONArray("choices") ?: run {
                AppLog.put("[${chapterTitle}] AI修正失败: 无choices")
                AppLog.put("[${chapterTitle}] AI修正完整响应: $body")
                throw CorrectionException("无choices")
            }
            if (choices.length() == 0) {
                AppLog.put("[${chapterTitle}] AI修正失败: choices为空")
                throw CorrectionException("choices为空")
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: run {
                    AppLog.put("[${chapterTitle}] AI修正失败: 无message")
                    throw CorrectionException("无message")
                }
            val result = message.optString("content") ?: run {
                AppLog.put("[${chapterTitle}] AI修正失败: 无content")
                throw CorrectionException("无content")
            }
            AppLog.put("[${chapterTitle}] AI修正成功 (来源: $source)")
            AppLog.put("[${chapterTitle}] AI修正校验通过: 长度 ${result.trim().length} (来源: $source)")
            validateResult(result, content, chapterTitle)
            parseResult(result)
        } catch (e: ValidationException) {
            AppLog.put("[${chapterTitle}] AI修正校验失败: ${e.message}")
            throw e
        } catch (e: CorrectionException) {
            AppLog.put("[${chapterTitle}] AI修正失败: ${e.message}")
            throw e
        } catch (e: Exception) {
            AppLog.put("[${chapterTitle}] AI修正异常: ${e.localizedMessage}")
            e.printStackTrace()
            throw CorrectionException("网络异常: ${e.localizedMessage}")
        }
    }

    /**
     * 校验 AI 返回结果的合法性
     * @param raw AI 返回的原始内容
     * @param originalContent 原始正文（用于长度对比）
     * @throws ValidationException 校验失败时抛出
     */
    private fun validateResult(raw: String, originalContent: String, chapterTitle: String = "") {
        val trimmed = raw.trim()

        // 1. 非空检查
        if (trimmed.isEmpty()) {
            AppLog.put("[${chapterTitle}] AI修正校验失败: 返回内容为空")
            throw ValidationException("返回内容为空")
        }

        // 2. 最小长度检查
        if (trimmed.length < MIN_VALID_LENGTH) {
            AppLog.put("[${chapterTitle}] AI修正校验失败: 返回内容过短 (${trimmed.length} < $MIN_VALID_LENGTH)")
            throw ValidationException("返回内容过短")
        }

        // 3. 长度上限检查（防止 AI 无限膨胀正文）
        val maxLength = (originalContent.length * MAX_RESULT_LENGTH_MULTIPLIER).toInt()
        if (trimmed.length > maxLength) {
            AppLog.put("[${chapterTitle}] AI修正校验失败: 返回内容过长 (${trimmed.length} > $maxLength)")
            throw ValidationException("返回内容过长，可能为异常输出")
        }

        // 4. 检查是否返回了纯解释性内容（AI 拒绝修正或无法理解时的回退）
        val nonContentMarkers = listOf(
            "我无法",
            "对不起，我",
            "抱歉，我",
            "无法修正",
            "无法处理",
            "这本书",
            "这个内容",
            "我不确定"
        )
        // 如果开头是这些内容，且长度很短，说明 AI 可能没做修正
        for (marker in nonContentMarkers) {
            if (trimmed.startsWith(marker) && trimmed.length < 200) {
                AppLog.put("[${chapterTitle}] AI修正校验失败: 检测到AI未执行修正，标记为 '$marker'")
                throw ValidationException("AI 未执行修正")
            }
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
