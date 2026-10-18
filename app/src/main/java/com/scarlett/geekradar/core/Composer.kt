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

    /** 结果页 / 复制使用的链接：原始来源 + 资源（若无资源则包含辅助链接中提供给模型的部分）。 */
    fun exportLinks(links: List<LinkView>): List<LinkView> {
        val res = links.filter { it.category == LinkCategory.RESOURCE }
        val src = links.filter { it.category == LinkCategory.SOURCE }
        return res + src
    }

    fun linkBlock(links: List<LinkView>): String {
        val list = exportLinks(links)
        if (list.isEmpty()) return ""
        return "🔗 相关链接\n" + list.joinToString("\n") { l ->
            val name = l.name.ifBlank { l.category.label }
            "$name：${l.best}"
        }
    }

    fun composeFull(title: String, body: String, links: List<LinkView>): String {
        val parts = ArrayList<String>()
        if (title.isNotBlank()) parts.add(title.trim())
        if (body.isNotBlank()) parts.add(expand(body.trim(), links))
        val block = linkBlock(links)
        if (block.isNotBlank()) parts.add(block)
        return parts.joinToString("\n\n")
    }

    fun allLinksText(links: List<LinkView>): String = links.joinToString("\n\n") { l ->
        "[${l.category.label}] ${l.name.ifBlank { l.linkId }}\n${l.best}"
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
