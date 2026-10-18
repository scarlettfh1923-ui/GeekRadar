package com.scarlett.geekradar.core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * DeepSeek（OpenAI 兼容）Chat Completions 客户端。API Key 只放在 Authorization 头里，绝不写日志。
 */
class DeepSeekClient(private val client: OkHttpClient = defaultClient()) {

    data class Config(val baseUrl: String, val apiKey: String, val model: String, val temperature: Double = 0.7)

    enum class ErrorKind(val label: String) {
        NO_KEY("未填写 API Key"),
        BAD_URL("Base URL 无效"),
        INVALID_KEY("API Key 无效"),
        INSUFFICIENT_BALANCE("余额不足"),
        RATE_LIMIT("请求过于频繁或额度限制"),
        MODEL_NOT_FOUND("模型名称不正确或不可用"),
        BAD_REQUEST("请求参数错误"),
        SERVER("模型服务异常或繁忙"),
        TIMEOUT("请求超时"),
        DNS("无法解析服务器地址（无网络或 DNS 失败）"),
        NETWORK("网络错误"),
        EMPTY("模型输出为空"),
        BAD_FORMAT("返回格式异常"),
        CANCELLED("已取消"),
    }

    sealed class Result {
        data class Success(val content: String, val model: String, val finishReason: String, val elapsedMs: Long) : Result()
        data class Failure(val kind: ErrorKind, val detail: String, val httpCode: Int = 0) : Result() {
            val message: String get() = kind.label + (if (detail.isNotBlank()) "：$detail" else "") + (if (httpCode > 0) "（HTTP $httpCode）" else "")
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        /** 2026-10 DeepSeek 官方文档：deepseek-flash（V4.1 Flash）；deepseek-chat 已于 2026-07-24 停用。 */
        const val DEFAULT_MODEL = "deepseek-flash"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()

        fun endpoint(base: String, path: String): String = base.trim().trimEnd('/') + path
    }

    suspend fun chat(cfg: Config, system: String, user: String): Result {
        if (cfg.apiKey.isBlank()) return Result.Failure(ErrorKind.NO_KEY, "请先在设置中填写 DeepSeek API Key")
        if (cfg.model.isBlank()) return Result.Failure(ErrorKind.MODEL_NOT_FOUND, "模型名称为空")
        val url = endpoint(cfg.baseUrl, "/chat/completions").toHttpUrlOrNull()
            ?: return Result.Failure(ErrorKind.BAD_URL, cfg.baseUrl)
        val body = JSONObject()
            .put("model", cfg.model.trim())
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("temperature", cfg.temperature)
            .put("stream", false)
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${cfg.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val start = System.currentTimeMillis()
        return try {
            client.newCall(req).await().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return mapHttpError(resp.code, text)
                val obj = runCatching { JSONObject(text) }.getOrNull()
                    ?: return Result.Failure(ErrorKind.BAD_FORMAT, "接口返回的不是 JSON：" + text.take(200))
                val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                    ?: return Result.Failure(ErrorKind.BAD_FORMAT, "缺少 choices 字段：" + text.take(200))
                val content = choice.optJSONObject("message")?.optString("content").orEmpty()
                if (content.isBlank()) return Result.Failure(ErrorKind.EMPTY, "finish_reason=" + choice.optString("finish_reason"))
                Result.Success(content, obj.optString("model", cfg.model), choice.optString("finish_reason"), System.currentTimeMillis() - start)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            mapException(e)
        }
    }

    /** 连接测试：GET /models，同时校验模型名是否在列表中（第三方兼容接口可能没有该端点）。 */
    suspend fun testConnection(cfg: Config): Result {
        if (cfg.apiKey.isBlank()) return Result.Failure(ErrorKind.NO_KEY, "请先填写 API Key")
        val url = endpoint(cfg.baseUrl, "/models").toHttpUrlOrNull() ?: return Result.Failure(ErrorKind.BAD_URL, cfg.baseUrl)
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${cfg.apiKey.trim()}").get().build()
        val start = System.currentTimeMillis()
        return try {
            client.newCall(req).await().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return mapHttpError(resp.code, text)
                val ids = runCatching {
                    val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
                    (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                }.getOrDefault(emptyList())
                val ms = System.currentTimeMillis() - start
                when {
                    ids.isEmpty() -> Result.Success("连接正常（接口未返回模型列表，无法校验模型名）", cfg.model, "", ms)
                    cfg.model.trim() in ids -> Result.Success("连接正常，模型 ${cfg.model} 可用", cfg.model, "", ms)
                    else -> Result.Failure(ErrorKind.MODEL_NOT_FOUND, "Key 有效，但模型列表中没有「${cfg.model}」。可用：${ids.joinToString(", ")}")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            mapException(e)
        }
    }

    internal fun mapHttpError(code: Int, body: String): Result.Failure {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: body.take(300)
        val lower = msg.lowercase()
        val kind = when {
            code == 401 || code == 403 -> ErrorKind.INVALID_KEY
            code == 402 || lower.contains("insufficient balance") -> ErrorKind.INSUFFICIENT_BALANCE
            code == 429 -> ErrorKind.RATE_LIMIT
            code == 404 -> ErrorKind.MODEL_NOT_FOUND
            (code == 400 || code == 422) && lower.contains("model") -> ErrorKind.MODEL_NOT_FOUND
            code == 400 || code == 422 -> ErrorKind.BAD_REQUEST
            code >= 500 -> ErrorKind.SERVER
            else -> ErrorKind.BAD_REQUEST
        }
        val extra = if (code == 404 && !lower.contains("model")) "接口地址不存在，请检查 Base URL 或模型名。$msg" else msg
        return Result.Failure(kind, extra, code)
    }

    internal fun mapException(e: Throwable): Result.Failure = when (e) {
        is UnknownHostException -> Result.Failure(ErrorKind.DNS, e.message ?: "")
        is SocketTimeoutException -> Result.Failure(ErrorKind.TIMEOUT, "服务器长时间未响应，可稍后重试")
        is java.io.InterruptedIOException -> Result.Failure(ErrorKind.TIMEOUT, "请求总时长超限")
        is IOException -> Result.Failure(ErrorKind.NETWORK, e.message ?: e.javaClass.simpleName)
        else -> Result.Failure(ErrorKind.NETWORK, e.message ?: e.javaClass.simpleName)
    }
}

/** OkHttp Call 的协程封装：协程取消时同时取消网络请求。 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
