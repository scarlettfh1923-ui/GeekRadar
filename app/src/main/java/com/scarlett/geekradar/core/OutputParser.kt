package com.scarlett.geekradar.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 容错解析模型输出。模型只能给出"建议"：链接建议只按内部 ID 关联，URL 字段一律忽略。
 * 解析失败时保留原始文本作为正文，绝不丢弃。
 */
object OutputParser {

    data class LinkSuggestion(val id: String, val name: String, val purpose: String, val category: LinkCategory?)

    data class Parsed(
        val structured: Boolean,
        val titles: List<String>,
        val body: String,
        val suggestions: List<LinkSuggestion>,
        val factChecks: List<String>,
        val uncertain: List<String>,
        val raw: String,
        val problem: String?,
        /** 话题标签（不带 #）。模型未给 tags 字段时，从正文末尾的 #标签 行中提取。 */
        val tags: List<String> = emptyList(),
    )

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        if (text.isEmpty()) return Parsed(false, emptyList(), "", emptyList(), emptyList(), emptyList(), raw, "模型输出为空")
        val obj = tryJson(text)
        if (obj != null) {
            val titles = strList(obj, "titles", "title_candidates", "标题", "标题候选").ifEmpty {
                listOfNotNull(obj.optString("title").takeIf { it.isNotBlank() })
            }
            val body = firstString(obj, "body", "content", "text", "正文", "post")
            if (titles.isNotEmpty() || body.isNotBlank()) {
                val sugg = ArrayList<LinkSuggestion>()
                val arr = obj.optJSONArray("links") ?: obj.optJSONArray("resources")
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim().uppercase().let { if (it.isNotEmpty() && !it.startsWith("L")) "L$it" else it }
                    if (id.isBlank()) continue
                    sugg.add(LinkSuggestion(id, o.optString("name").trim(), o.optString("purpose").ifBlank { o.optString("description") }.trim(),
                        LinkCategory.fromModelKey(o.optString("category").ifBlank { o.optString("type") })))
                }
                val problem = when {
                    body.isBlank() -> "模型未返回正文字段，请检查提示词或重新生成"
                    titles.isEmpty() -> "模型未返回标题候选"
                    else -> null
                }
                val (cleanBody, trailing) = Tags.extractTrailing(body.trim())
                val tags = Tags.merge(strList(obj, "tags", "hashtags", "topics", "标签", "话题").flatMap { it.split(Regex("""[\s,，、]+""")) }, trailing)
                return Parsed(true, titles.take(5), cleanBody.trim(), sugg,
                    strList(obj, "fact_checks", "factChecks", "事实核查"), strList(obj, "uncertain", "uncertainties", "不确定信息"), raw, problem, tags)
            }
        }
        // 非结构化 / 残缺 JSON：尽量抢救字段，否则整段原文作为正文。
        val salvagedBody = Regex(""""body"\s*:\s*"((?:[^"\\]|\\.)*)""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.let(::unescape)
        val salvagedTitles = Regex(""""titles"\s*:\s*\[(.*?)(\]|$)""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            ?.let { Regex(""""((?:[^"\\]|\\.)*)"""").findAll(it).map { m -> unescape(m.groupValues[1]) }.toList() } ?: emptyList()
        return if (!salvagedBody.isNullOrBlank()) {
            val (cleanBody, trailing) = Tags.extractTrailing(salvagedBody)
            Parsed(false, salvagedTitles, cleanBody, emptyList(), emptyList(), emptyList(), raw, "模型返回的 JSON 不完整，已尽量提取正文，请核对或重新生成", trailing)
        } else {
            Parsed(false, salvagedTitles, stripFence(text), emptyList(), emptyList(), emptyList(), raw,
                if (looksLikeJson(text)) "模型返回格式异常（JSON 无法解析），已保留原始输出" else "模型输出了非标准结构，已原样放入正文，可手动编辑")
        }
    }

    private fun looksLikeJson(t: String) = stripFence(t).trimStart().startsWith("{")

    fun stripFence(t: String): String {
        val s = t.trim()
        val m = Regex("^```[a-zA-Z]*\\s*\\n(.*?)\\n?```\\s*$", RegexOption.DOT_MATCHES_ALL).find(s)
        return m?.groupValues?.get(1)?.trim() ?: s
    }

    private fun tryJson(t: String): JSONObject? {
        val s = stripFence(t)
        val a = s.indexOf('{'); val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        val cand = s.substring(a, b + 1)
        return runCatching { JSONObject(cand) }.getOrNull()
            ?: runCatching { JSONObject(cand.replace(Regex(",\\s*([}\\]])"), "$1")) }.getOrNull()
    }

    private fun firstString(o: JSONObject, vararg keys: String): String {
        for (k in keys) { val v = o.opt(k); if (v is String && v.isNotBlank()) return v }
        return ""
    }

    private fun strList(o: JSONObject, vararg keys: String): List<String> {
        for (k in keys) {
            val v = o.opt(k)
            if (v is JSONArray) return (0 until v.length()).mapNotNull { i ->
                when (val x = v.opt(i)) { is String -> x.trim(); is JSONObject -> (x.optString("text").ifBlank { x.optString("title") }).trim(); else -> null }
            }.filter { it.isNotBlank() }
            if (v is String && v.isNotBlank()) return listOf(v.trim())
        }
        return emptyList()
    }

    private fun unescape(s: String): String = runCatching { JSONArray("[\"$s\"]").getString(0) }.getOrElse {
        s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
