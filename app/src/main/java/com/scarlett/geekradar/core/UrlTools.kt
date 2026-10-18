package com.scarlett.geekradar.core

import org.jsoup.internal.StringUtil
import java.net.URI

/**
 * URL 识别与辅助工具。
 *
 * 链接保护原则：这里的所有函数都只"读取"URL，返回新的派生值（解析后地址、匹配用规范化值），
 * 绝不修改或替换原始字符串。原始字符串始终以用户/网页给出的为准。
 */
object UrlTools {

    /** 匹配 http(s) URL；遇到空白、引号、尖括号、中日韩字符与全角标点即停止。 */
    private val URL_REGEX = Regex(
        """(?i)\bhttps?://[^\s<>"'`\u3000-\u303F\uFF00-\uFFEF\u4E00-\u9FFF\u2018-\u201F]+"""
    )
    private const val TRAILING = ".,;:!?'\"]}>*_~"

    data class UrlMatch(val raw: String, val start: Int, val end: Int)

    /** 提取文本中全部 URL，按出现顺序去重（以原始字符串为准）。 */
    fun extractUrls(text: String): List<UrlMatch> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<UrlMatch>()
        for (m in URL_REGEX.findAll(text)) {
            var s = m.value
            s = trimTrailing(s)
            if (s.length <= "https://".length) continue
            if (seen.add(s)) out.add(UrlMatch(s, m.range.first, m.range.first + s.length))
        }
        return out
    }

    /** 去掉句末标点；右括号只在不成对时才去掉（例如维基百科 URL 中的成对括号要保留）。 */
    internal fun trimTrailing(input: String): String {
        var s = input
        while (s.isNotEmpty()) {
            val c = s.last()
            if (c == ')') {
                if (s.count { it == ')' } > s.count { it == '(' }) { s = s.dropLast(1); continue } else break
            }
            if (TRAILING.indexOf(c) >= 0) s = s.dropLast(1) else break
        }
        return s
    }

    enum class InputKind { EMPTY, URL_ONLY, TEXT_WITH_URLS, TEXT }

    data class InputAnalysis(
        val kind: InputKind,
        val urls: List<String>,
        /** 去掉 URL 后剩余文字长度，用于判断是"附言+链接"还是"一篇原文"。 */
        val nonUrlTextLength: Int,
    ) {
        /** 剩余文字较长时视为原文粘贴（不自动抓取，但可手动抓取主链接）。 */
        val looksLikeArticle: Boolean get() = nonUrlTextLength >= 200
    }

    fun analyze(text: String): InputAnalysis {
        val t = text.trim()
        if (t.isEmpty()) return InputAnalysis(InputKind.EMPTY, emptyList(), 0)
        val urls = extractUrls(t)
        var rest = t
        urls.forEach { rest = rest.replace(it.raw, " ") }
        val restLen = rest.replace(Regex("\\s+"), "").length
        val kind = when {
            urls.isEmpty() -> InputKind.TEXT
            restLen == 0 -> InputKind.URL_ONLY
            else -> InputKind.TEXT_WITH_URLS
        }
        return InputAnalysis(kind, urls.map { it.raw }, restLen)
    }

    fun isHttpUrl(s: String): Boolean {
        val l = s.trim().lowercase()
        return (l.startsWith("http://") || l.startsWith("https://")) && l.length > 9 && !l.contains(' ')
    }

    /** 基于网页地址把相对链接解析为绝对地址；解析失败时返回空字符串（原始值另行保存）。 */
    fun resolve(base: String, raw: String): String {
        val r = raw.trim()
        if (r.isEmpty()) return ""
        return try { StringUtil.resolve(base, r) } catch (_: Exception) { "" }
    }

    /**
     * 仅用于"匹配/查重"的规范化值：小写协议与主机、去掉默认端口、去掉 #片段 与末尾斜杠。
     * 不改变查询参数，也不把 http 与 https 视为相同。绝不用于覆盖原始值。
     */
    fun normalizeForMatch(url: String): String {
        return try {
            val u = URI(url.trim())
            val scheme = (u.scheme ?: "").lowercase()
            val host = (u.host ?: return url.trim()).lowercase()
            val port = if (u.port == -1 || (scheme == "http" && u.port == 80) || (scheme == "https" && u.port == 443)) "" else ":${u.port}"
            val path = (u.rawPath ?: "").trimEnd('/')
            val q = u.rawQuery?.let { "?$it" } ?: ""
            "$scheme://$host$port$path$q"
        } catch (_: Exception) {
            url.trim().substringBefore('#').trimEnd('/')
        }
    }

    fun hostOf(url: String): String = try { URI(url.trim()).host?.lowercase() ?: "" } catch (_: Exception) {
        Regex("(?i)^https?://([^/?#:]+)").find(url)?.groupValues?.get(1)?.lowercase() ?: ""
    }

    fun pathOf(url: String): String = try { URI(url.trim()).rawPath ?: "" } catch (_: Exception) { "" }
}
