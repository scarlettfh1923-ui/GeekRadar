package com.scarlett.geekradar.core

/**
 * 封面图排版的纯逻辑部分（不依赖 Android，便于单元测试）。
 * 渲染见 ui/share/CoverRenderer.kt。封面只使用标题、正文首句和标签，不含任何原作者信息。
 */
object CoverLayout {

    /** 封面需要的文字。 */
    data class Content(val kicker: String, val number: String, val title: String, val subtitle: String, val footerLeft: String, val footerRight: String)

    private val EMOJI = Regex("""[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}\x{200D}\x{2B00}-\x{2BFF}]""")
    private val PLACEHOLDER = Regex("""[\[【{]\s*L\d{1,4}\s*[\]】}]""")
    private val URL = Regex("""https?://\S+""")

    /** 去掉 emoji、链接编号与 URL，用于封面文字。 */
    fun clean(s: String): String = s.replace(PLACEHOLDER, "").replace(URL, "").replace(EMOJI, "").replace(Regex("""[ \t]+"""), " ").trim()

    fun content(taskId: Long, title: String, body: String, tags: List<String>): Content {
        val t = clean(title).ifBlank { "极客雷达" }
        val firstLine = body.lines().map { clean(it).trim().trimStart('✅', '-', '·', '•', '*').trim() }
            .firstOrNull { it.length >= 6 && !it.startsWith("#") } ?: ""
        val sub = firstSentence(firstLine)
        val kicker = tags.firstOrNull()?.let { tg -> if (tg.endsWith("推荐")) tg else "${tg}推荐" } ?: "好物推荐"
        val foot = tags.drop(1).take(2).joinToString(" · ").ifBlank { "收藏备用" }
        return Content(kicker.take(14), "No.%02d".format(taskId.coerceAtLeast(1)), t, sub, foot, "极客雷达")
    }

    private fun firstSentence(s: String): String {
        if (s.isBlank()) return ""
        val idx = s.indexOfFirst { it in "。！？!?" }
        val cut = if (idx in 8..60) s.substring(0, idx + 1) else s
        return cut.take(60)
    }

    /** 切词：连续的拉丁字母 / 数字（含 - _ . ）作为一个整体，其余字符逐字。空格单独成词。 */
    fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() } }
        for (ch in text) {
            when {
                ch.isLetterOrDigit() && ch.code < 0x2E80 || ch in "-_.+'" -> sb.append(ch)
                else -> { flush(); out.add(ch.toString()) }
            }
        }
        flush()
        return out
    }

    private const val NO_LINE_START = "，。！？、；：,.!?;:）)》」』”’…"

    /** 按宽度贪心换行；单词过长时按字符断开；行首禁则标点挂在上一行末尾。 */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val lines = ArrayList<String>()
        for (para in text.split('\n')) {
            val cur = StringBuilder()
            fun breakLine() { if (cur.isNotBlank()) lines.add(cur.toString().trim()); cur.clear() }
            for (tok in tokens(para)) {
                if (tok == " " && cur.isEmpty()) continue
                if (measure((cur.toString() + tok).trimEnd()) <= maxWidth) { cur.append(tok); continue }
                if (tok.length == 1 && tok[0] in NO_LINE_START && cur.isNotEmpty()) { cur.append(tok); continue }
                breakLine()
                if (tok == " ") continue
                if (measure(tok) <= maxWidth) { cur.append(tok); continue }
                for (ch in tok) {
                    if (cur.isNotEmpty() && measure(cur.toString() + ch) > maxWidth) breakLine()
                    cur.append(ch)
                }
            }
            breakLine()
        }
        return lines
    }

    /**
     * 从大到小尝试字号，找到让标题不超过 maxLines 行的最大字号；
     * 再在该行数下收窄排版宽度，让各行长度尽量均衡（避免最后一行只剩一两个字）。
     */
    fun fitTitle(title: String, maxWidth: Float, sizes: List<Float>, maxLines: Int, measureAt: (String, Float) -> Float): Pair<Float, List<String>> {
        for (size in sizes) {
            val m: (String) -> Float = { measureAt(it, size) }
            val lines = wrap(title, maxWidth, m)
            if (lines.size <= maxLines) return size to balance(title, maxWidth, lines.size, m)
        }
        val size = sizes.last()
        val lines = wrap(title, maxWidth) { measureAt(it, size) }
        if (lines.size <= maxLines) return size to lines
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = kept[maxLines - 1].dropLast(1) + "…"
        return size to kept
    }

    /** 二分查找仍保持 n 行的最小宽度，用它重新换行，使各行更均衡。 */
    fun balance(text: String, maxWidth: Float, n: Int, measure: (String) -> Float): List<String> {
        if (n <= 1) return wrap(text, maxWidth, measure)
        var lo = maxWidth * 0.4f; var hi = maxWidth
        repeat(18) {
            val mid = (lo + hi) / 2
            if (wrap(text, mid, measure).size <= n) hi = mid else lo = mid
        }
        return wrap(text, hi, measure)
    }
}
