package com.scarlett.geekradar.core

/**
 * 由程序（而非模型）组合最终文案与链接清单；正文中的 [L2] 占位符在复制时按内部 ID 回填原始 URL。
 */
object Composer {

    data class LinkView(val linkId: String, val raw: String, val resolved: String, val category: LinkCategory, val name: String, val sendToModel: Boolean) {
        /** 可直接使用的地址：原始值本身是绝对 http(s) URL 时用原始值（原样），否则用解析后的绝对地址。 */
        val best: String get() = if (UrlTools.isHttpUrl(raw)) raw else resolved.ifBlank { raw }
    }

    private val PLACEHOLDER = Regex("""[\[【{]\s*(L\d{1,4})\s*[\]】}]""")

    fun placeholdersIn(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toList()

    /** 把 [Ln] 回填为原始 URL 字符串；不存在的 ID 原样保留并由 [checkLinks] 给出警告。 */
    fun expand(text: String, links: List<LinkView>): String {
        val map = links.associateBy { it.linkId }
        return PLACEHOLDER.replace(text) { m -> map[m.groupValues[1]]?.best ?: m.value }
    }

    /** 「复制完整文案」附带的链接：只有资源链接。来源类（原帖 / 作者主页）永不进入文案。 */
    fun exportLinks(links: List<LinkView>): List<LinkView> = links.filter { it.category == LinkCategory.RESOURCE }

    fun linkBlock(links: List<LinkView>): String {
        val list = exportLinks(links)
        if (list.isEmpty()) return ""
        return "🔗 相关链接\n" + list.joinToString("\n") { l ->
            val name = l.name.ifBlank { l.category.label }
            "$name：${l.best}"
        }
    }

    /** 完整文案 = 标题 + 正文（编号回填为原始链接）+ 资源链接 + 话题标签。 */
    fun composeFull(title: String, body: String, links: List<LinkView>, tags: List<String> = emptyList()): String {
        val parts = ArrayList<String>()
        if (title.isNotBlank()) parts.add(title.trim())
        if (body.isNotBlank()) parts.add(expand(body.trim(), links))
        val block = linkBlock(links)
        if (block.isNotBlank()) parts.add(block)
        val tagLine = Tags.line(tags)
        if (tagLine.isNotBlank()) parts.add(tagLine)
        return parts.joinToString("\n\n")
    }

    /** 「复制全部链接」：不含来源类链接（原帖 / 作者主页），它们只留在应用内的链接清单里。 */
    fun allLinksText(links: List<LinkView>): String = links.filter { it.category != LinkCategory.SOURCE }.joinToString("\n\n") { l ->
        "[${l.category.label}] ${l.name.ifBlank { l.linkId }}\n${l.best}"
    }

    // ---------------- 来源信息安全网 ----------------

    private val AUTHOR_WORDS = Regex("""原帖|原推|原贴|原博|原文链接|原文地址|原文见|作者主页|博主主页|个人主页|作者表示|作者称|作者介绍|据作者|博主推荐|博主分享|楼主|发帖人|转自|via\s*@""", RegexOption.IGNORE_CASE)
    private val AT_HANDLE = Regex("""(?<![A-Za-z0-9._%+-])@([A-Za-z0-9_]{2,30})""")

    /** 从来源链接（如 x.com/<user>/status/…）中取出账号名。 */
    fun handlesOf(links: List<LinkView>): Set<String> = links.filter { it.category == LinkCategory.SOURCE }.mapNotNull { l ->
        val url = l.best
        val host = UrlTools.hostOf(url).removePrefix("www.").removePrefix("mobile.")
        val seg = UrlTools.pathOf(url).split('/').firstOrNull { it.isNotEmpty() } ?: return@mapNotNull null
        if (host in setOf("x.com", "twitter.com", "threads.net", "threads.com", "instagram.com", "medium.com", "youtube.com", "t.me"))
            seg.removePrefix("@").takeIf { it.length >= 3 && it.lowercase() !in setOf("home", "explore", "search", "channel", "user") } else null
    }.toSet()

    /**
     * 生成后 / 编辑时的提醒：文案里仍含来源链接、来源编号、「原帖 / 作者主页」等字样或发帖人账号。
     * 只提醒，不改动用户文字。
     */
    fun sourceMentionWarnings(texts: List<String>, links: List<LinkView>): List<String> {
        val text = texts.joinToString("\n")
        if (text.isBlank()) return emptyList()
        val out = ArrayList<String>()
        val src = links.filter { it.category == LinkCategory.SOURCE }
        val urlHits = src.filter { l -> listOf(l.raw, l.resolved).any { it.isNotBlank() && text.contains(it.trim()) } }.map { it.linkId }
        val refHits = placeholdersIn(text).filter { id -> src.any { it.linkId == id } }
        val hits = (urlHits + refHits).distinct()
        if (hits.isNotEmpty()) out.add("文案引用了来源链接 ${hits.joinToString("、")}（原帖 / 作者主页），复制和分享时会被带出，建议删除")
        val words = AUTHOR_WORDS.findAll(text).map { it.value }.distinct().toList()
        if (words.isNotEmpty()) out.add("文案中出现「${words.joinToString("、")}」等原帖 / 作者相关字样，建议改成直接介绍资源本身")
        val handles = handlesOf(links)
        val named = handles.filter { h -> Regex("""(?<![A-Za-z0-9_])@?${Regex.escape(h)}(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        val ats = AT_HANDLE.findAll(text).map { "@" + it.groupValues[1] }.distinct().toList()
        val accountHits = (named.map { "@$it" } + ats).distinctBy { it.lowercase() }
        if (accountHits.isNotEmpty()) out.add("文案中出现账号名 ${accountHits.joinToString("、")}，可能是原发帖人信息，请确认是否删除")
        return out
    }

    /**
     * 生成后差异检查：
     *  - 模型文本中出现、但不在原始清单（原始值或解析值）中的 URL；
     *  - 模型引用了不存在的链接 ID；
     *  - 模型建议中出现未知 ID。
     */
    fun checkLinks(texts: List<String>, links: List<LinkView>, suggestedIds: List<String>): List<String> {
        val known = HashSet<String>()
        links.forEach { known.add(it.raw.trim()); known.add(it.resolved.trim()) }
        val ids = links.map { it.linkId }.toSet()
        val warnings = ArrayList<String>()
        val foreign = texts.flatMap { t -> UrlTools.extractUrls(t).map { it.raw } }.distinct().filter { it !in known }
        if (foreign.isNotEmpty()) warnings.add("模型文案中出现了 ${foreign.size} 个不在原始链接清单中的 URL（可能被改写或编造），请核对并删除：" + foreign.joinToString("、"))
        val badRefs = texts.flatMap { placeholdersIn(it) }.distinct().filter { it !in ids }
        if (badRefs.isNotEmpty()) warnings.add("正文引用了不存在的链接编号：${badRefs.joinToString("、")}，复制时不会被替换")
        val badSugg = suggestedIds.distinct().filter { it !in ids }
        if (badSugg.isNotEmpty()) warnings.add("模型返回了无法匹配的链接编号：${badSugg.joinToString("、")}，已忽略，未做任何猜测补全")
        return warnings
    }
}
