package com.scarlett.geekradar.core

import org.json.JSONArray

/** 话题标签工具：统一存储为不带 # 的短词，分享 / 复制时再拼成「#标签 #标签」。 */
object Tags {
    const val MAX_LEN = 20
    private val SPLIT = Regex("""[\s,，、;；#＃]+""")
    private val TAG_TOKEN = Regex("""[#＃]([^\s#＃,，、]+)""")

    /** 规范化单个标签：去掉 #、首尾空白和内部空白；空则返回 null。 */
    fun normalize(s: String): String? {
        val t = s.trim().trimStart('#', '＃').trim().replace(Regex("""\s+"""), "").trimEnd('#', '＃')
        return t.take(MAX_LEN).ifBlank { null }
    }

    /** 把用户输入（空格、逗号或 # 分隔）解析为多个标签。 */
    fun parseInput(text: String): List<String> = merge(text.split(SPLIT).mapNotNull { normalize(it) })

    /** 合并多个列表，按出现顺序去重（忽略大小写）。 */
    fun merge(vararg lists: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (l in lists) for (raw in l) {
            val n = normalize(raw) ?: continue
            if (seen.add(n.lowercase())) out.add(n)
        }
        return out
    }

    /** 分享 / 复制用的标签行：#AI工具 #开源。 */
    fun line(tags: List<String>): String = tags.mapNotNull { normalize(it) }.joinToString(" ") { "#$it" }

    /**
     * 拆出正文末尾"只由 #标签 组成"的行（旧版提示词会把标签写在正文末尾）。
     * 返回 (去掉这些行后的正文, 标签)。正文中间的 # 不受影响。
     */
    fun extractTrailing(body: String): Pair<String, List<String>> {
        val lines = body.trimEnd().lines().toMutableList()
        val found = ArrayList<List<String>>()
        while (lines.isNotEmpty()) {
            val last = lines.last().trim()
            if (last.isEmpty()) { lines.removeAt(lines.lastIndex); continue }
            val rest = TAG_TOKEN.replace(last, "").trim()
            val tokens = TAG_TOKEN.findAll(last).map { it.groupValues[1] }.toList()
            if (tokens.isNotEmpty() && rest.isEmpty()) { found.add(0, tokens); lines.removeAt(lines.lastIndex) } else break
        }
        if (found.isEmpty()) return body to emptyList()
        return lines.joinToString("\n").trimEnd() to merge(*found.toTypedArray())
    }

    fun toJson(tags: List<String>): String = JSONArray(tags).toString()
    fun fromJson(s: String?): List<String> = runCatching {
        val a = JSONArray(s ?: "[]"); (0 until a.length()).mapNotNull { normalize(a.optString(it)) }
    }.getOrDefault(emptyList())
}
