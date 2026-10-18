package com.scarlett.geekradar.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * 网页抓取：先用本机 OkHttp + Jsoup；失败或正文过少时（且用户开启）再走备用读取通道 Jina Reader。
 * 不绕过登录、不破解访问控制；拿不到内容就如实返回失败，由界面引导用户粘贴原文。
 */
class PageFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val jinaBase: String = "https://r.jina.ai/",
    /** 可选 Jina API Key（部分网络环境下匿名调用会被拒绝）。 */
    private val jinaKeyProvider: () -> String = { "" },
) {
    enum class Status { OK, PARTIAL, FAILED }

    data class Outcome(
        val status: Status,
        val channel: String,           // local / jina / none
        val finalUrl: String,
        val title: String = "",
        val description: String = "",
        val text: String = "",
        val links: List<ExtractedLink> = emptyList(),
        val error: String = "",
        val localError: String = "",
        val jinaError: String = "",
        val jinaTried: Boolean = false,
    )

    companion object {
        const val MIN_TEXT = 200
        const val MAX_BYTES = 4 * 1024 * 1024
        const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true)
            .build()

        fun describe(e: Throwable): String = when (e) {
            is UnknownHostException -> "无法解析域名：DNS 失败或当前无网络"
            is SocketTimeoutException -> "网页请求超时"
            is ConnectException -> "连接失败：服务器无响应或网络不可用"
            is SSLException -> "安全连接（TLS）失败：${e.message ?: ""}"
            is java.io.InterruptedIOException -> "网页请求超时"
            else -> "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}"
        }

        fun httpError(code: Int): String = when (code) {
            401, 403 -> "网页拒绝访问或需要登录（HTTP $code）"
            404 -> "网页不存在（HTTP 404）"
            429 -> "访问过于频繁，被网站限流（HTTP 429）"
            in 500..599 -> "网站服务器异常（HTTP $code）"
            else -> "服务器返回异常状态（HTTP $code）"
        }
    }

    suspend fun fetch(rawUrl: String, useJina: Boolean): Outcome = withContext(Dispatchers.IO) {
        val local = fetchLocal(rawUrl)
        if (local.status == Status.OK || !useJina) return@withContext local
        val jina = fetchJina(rawUrl)
        val localLen = local.text.length
        if (jina.status != Status.FAILED && jina.text.length > localLen) {
            // 合并两条通道发现的链接：任何一条通道拿到的 URL 都保留。
            jina.copy(
                title = jina.title.ifBlank { local.title },
                description = local.description.ifBlank { jina.description },
                links = local.links + jina.links,
                localError = local.error.ifBlank { "本地仅提取到 $localLen 字" },
                jinaTried = true,
            )
        } else {
            local.copy(
                jinaTried = true,
                jinaError = jina.error.ifBlank { "备用通道未获得更多内容" },
                links = local.links + jina.links,
            )
        }
    }

    fun fetchLocal(rawUrl: String): Outcome {
        val url = rawUrl.trim()
        val httpUrl = url.toHttpUrlOrNull()
            ?: return Outcome(Status.FAILED, "none", url, error = "URL 格式错误，无法访问")
        return try {
            val req = Request.Builder().url(httpUrl)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (!resp.isSuccessful) return Outcome(Status.FAILED, "local", finalUrl, error = httpError(resp.code))
                val body = resp.body ?: return Outcome(Status.FAILED, "local", finalUrl, error = "页面内容为空")
                val type = body.contentType()
                val sub = "${type?.type}/${type?.subtype}".lowercase()
                if (type != null && !(sub.contains("html") || sub.contains("xml") || type.type == "text")) {
                    return Outcome(Status.FAILED, "local", finalUrl, error = "不是网页内容（$sub），可能是下载文件")
                }
                val bytes = readLimited(body.byteStream())
                if (bytes.isEmpty()) return Outcome(Status.FAILED, "local", finalUrl, error = "页面内容为空")
                // 相对链接以最终（重定向后）的网页地址为基准解析。
                val page = if (sub.contains("html") || sub.contains("xml") || type == null) {
                    val doc = Jsoup.parse(ByteArrayInputStream(bytes), type?.charset()?.name(), finalUrl)
                    HtmlExtractor.extract(doc)
                } else {
                    val txt = String(bytes, type.charset() ?: Charsets.UTF_8)
                    HtmlExtractor.PageContent("", "", txt, UrlTools.extractUrls(txt).map {
                        ExtractedLink(it.raw, it.raw, "", LinkLocation.CONTENT)
                    })
                }
                val status = when {
                    page.text.isBlank() -> Status.FAILED
                    page.text.length < MIN_TEXT -> Status.PARTIAL
                    else -> Status.OK
                }
                Outcome(
                    status, "local", finalUrl, page.title, page.description, page.text, page.links,
                    error = when (status) {
                        Status.FAILED -> "页面内容为空（可能需要登录或由脚本动态加载）"
                        Status.PARTIAL -> "只提取到部分内容（${page.text.length} 字）"
                        else -> ""
                    },
                )
            }
        } catch (e: Exception) {
            Outcome(Status.FAILED, "local", url, error = describe(e))
        }
    }

    /** 备用读取通道：GET https://r.jina.ai/<原始URL>。原始 URL 原样拼接，不做任何改写。 */
    fun fetchJina(rawUrl: String): Outcome {
        val url = rawUrl.trim()
        val target = jinaBase + url
        val httpUrl = target.toHttpUrlOrNull()
            ?: return Outcome(Status.FAILED, "jina", url, error = "备用通道：URL 无法拼接")
        return try {
            val req = Request.Builder().url(httpUrl)
                .header("Accept", "text/plain")
                .header("X-Return-Format", "markdown")
                .apply { jinaKeyProvider().trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Outcome(Status.FAILED, "jina", url, error = "备用通道失败：" +
                    if (resp.code == 401 || resp.code == 403) "Jina 拒绝匿名访问（HTTP ${resp.code}），可在设置中填写 Jina API Key" else httpError(resp.code))
                val txt = resp.body?.let { String(readLimited(it.byteStream()), Charsets.UTF_8) } ?: ""
                val parsed = JinaParser.parse(txt, url)
                val status = when {
                    parsed.text.isBlank() -> Status.FAILED
                    parsed.text.length < MIN_TEXT -> Status.PARTIAL
                    else -> Status.OK
                }
                Outcome(status, "jina", url, parsed.title, "", parsed.text, parsed.links,
                    error = if (status == Status.FAILED) "备用通道返回内容为空" else if (status == Status.PARTIAL) "备用通道只获得部分内容" else "")
            }
        } catch (e: Exception) {
            Outcome(Status.FAILED, "jina", url, error = "备用通道：" + describe(e))
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        input.use {
            while (true) {
                val n = it.read(buf); if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() >= MAX_BYTES) break
            }
        }
        return out.toByteArray()
    }
}

/** 解析 Jina Reader 的 Markdown 输出，链接同样按原始字符串保存。 */
object JinaParser {
    data class Parsed(val title: String, val text: String, val links: List<ExtractedLink>)

    private val MD_LINK = Regex("""(!?)\[([^\]\n]*)\]\(\s*(<[^>\n]+>|[^)\s]+)(?:\s+"[^"\n]*")?\s*\)""")
    private val IMAGE_EXT = Regex("""(?i)\.(png|jpe?g|gif|webp|svg|ico|bmp)(\?.*)?$""")

    fun parse(raw: String, base: String): Parsed {
        var title = ""
        var content = raw
        val lines = raw.lines()
        lines.take(8).forEach { l -> if (l.startsWith("Title:")) title = l.removePrefix("Title:").trim() }
        val idx = raw.indexOf("Markdown Content:")
        if (idx >= 0) content = raw.substring(idx + "Markdown Content:".length).trim()
        val links = ArrayList<ExtractedLink>()
        val covered = HashSet<String>()
        for (m in MD_LINK.findAll(content)) {
            val isImage = m.groupValues[1] == "!"
            var u = m.groupValues[3]
            if (u.startsWith("<") && u.endsWith(">")) u = u.substring(1, u.length - 1)
            covered.add(u)
            if (isImage) continue
            if (u.startsWith("#") || u.startsWith("mailto:", true) || u.startsWith("javascript:", true)) continue
            val resolved = if (UrlTools.isHttpUrl(u)) u else UrlTools.resolve(base, u)
            if (!UrlTools.isHttpUrl(resolved)) continue
            links.add(ExtractedLink(u, resolved, m.groupValues[2].take(120), LinkLocation.JINA))
        }
        for (m in UrlTools.extractUrls(content)) {
            if (m.raw in covered || covered.any { it.startsWith(m.raw) || m.raw.startsWith(it) }) continue
            if (IMAGE_EXT.containsMatchIn(m.raw)) continue
            links.add(ExtractedLink(m.raw, m.raw, "", LinkLocation.JINA))
        }
        return Parsed(title, content, links)
    }
}
