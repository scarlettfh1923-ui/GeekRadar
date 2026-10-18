package com.scarlett.geekradar.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * 纯本地的网页正文与链接提取（不执行任何网页脚本，Jsoup 只做静态解析）。
 */
object HtmlExtractor {

    data class PageContent(
        val title: String,
        val description: String,
        val text: String,
        val links: List<ExtractedLink>,
    )

    /** 常见正文容器，按优先级排列（公众号、GitHub README、博客、文档站）。 */
    private val CONTENT_SELECTORS = listOf(
        "#js_content", ".rich_media_content",
        "article.markdown-body", ".markdown-body",
        "article", "main article", "[itemprop=articleBody]",
        ".post-content", ".entry-content", ".article-content", ".article-body", ".post-body",
        "#content article", "main", "[role=main]", "#content", ".content",
    )
    private const val STRIP = "script,style,noscript,template,iframe,svg,canvas,form,button,input,select,nav,header,footer,aside,[aria-hidden=true],.sidebar,.advert,.ads,.ad,.share,.comments,#comments"

    fun extract(html: String, baseUrl: String): PageContent = extract(Jsoup.parse(html, baseUrl))

    fun extract(doc: Document): PageContent {
        val title = firstNonBlank(
            doc.selectFirst("#activity-name")?.text(),
            doc.selectFirst("meta[property=og:title]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content"),
            doc.title(),
            doc.selectFirst("h1")?.text(),
        )
        val description = firstNonBlank(
            doc.selectFirst("meta[name=description]")?.attr("content"),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:description]")?.attr("content"),
        )
        val contentEl = pickContent(doc)
        val text = contentEl?.let { toText(it.clone().also { c -> c.select(STRIP).remove() }) } ?: ""
        val bodyText = if (text.length < 200) {
            doc.body()?.let { toText(it.clone().also { c -> c.select(STRIP).remove() }) } ?: ""
        } else ""
        val finalText = if (bodyText.length > text.length) bodyText else text

        // 保留原始 HTML 中全部有效的超链接（不只是正文中的）。
        val links = ArrayList<ExtractedLink>()
        for (a in doc.select("a[href], area[href]")) {
            val raw = a.attr("href")
            if (raw.isBlank()) continue
            val t = raw.trim()
            if (t.startsWith("#") || t.startsWith("javascript:", true) || t.startsWith("mailto:", true) || t.startsWith("tel:", true)) continue
            val resolved = a.absUrl("href").ifBlank { UrlTools.resolve(doc.location(), raw) }
            if (!UrlTools.isHttpUrl(resolved)) continue
            val inContent = contentEl != null && isDescendant(a, contentEl) && !insideChrome(a, contentEl)
            val anchor = a.text().ifBlank { a.attr("title").ifBlank { a.attr("aria-label") } }.take(120)
            links.add(ExtractedLink(raw, resolved, anchor, if (inContent) LinkLocation.CONTENT else LinkLocation.PAGE))
        }
        return PageContent(title.trim(), description.trim(), finalText.take(80_000), links)
    }

    private fun pickContent(doc: Document): Element? {
        for (sel in CONTENT_SELECTORS) {
            val el = doc.select(sel).maxByOrNull { it.text().length } ?: continue
            if (el.text().length >= 200) return el
        }
        return doc.body()
    }

    private fun isDescendant(el: Element, ancestor: Element): Boolean {
        var p: Element? = el
        while (p != null) { if (p === ancestor) return true; p = p.parent() }
        return false
    }

    private fun insideChrome(el: Element, stop: Element): Boolean {
        var p: Element? = el.parent()
        while (p != null && p !== stop) {
            val n = p.normalName()
            if (n == "nav" || n == "footer" || n == "header" || n == "aside") return true
            p = p.parent()
        }
        return false
    }

    /** 把 DOM 转为按块分行的纯文本。 */
    fun toText(el: Element): String {
        val sb = StringBuilder()
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when {
                    node is TextNode -> sb.append(node.text())
                    node is Element && (node.normalName() == "br") -> sb.append('\n')
                    node is Element && node.tag().isBlock -> sb.append('\n')
                }
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.tag().isBlock) sb.append('\n')
            }
        }, el)
        return sb.toString().lines()
            .map { it.replace(Regex("[ \\t\\u00A0\\u200B]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun firstNonBlank(vararg s: String?): String = s.firstOrNull { !it.isNullOrBlank() } ?: ""
}
