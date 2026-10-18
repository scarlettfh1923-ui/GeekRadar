package com.scarlett.geekradar.core

/**
 * 程序端链接编号与初步分类（在调用 AI 之前完成）。
 * 规则：
 *  - 以"解析后地址"完全相同为重复判据，只合并真正相同的链接并累计出现次数；
 *  - 规范化后相同但原始写法不同的链接不会合并，只加备注提示"可能重复"；
 *  - 原始字符串永远保留为首次出现时的写法。
 */
object LinkClassifier {

    private val GITHUB_RESERVED = setOf(
        "features", "topics", "login", "signup", "join", "about", "pricing", "marketplace", "sponsors", "explore",
        "settings", "orgs", "collections", "trending", "enterprise", "team", "customer-stories", "security",
        "site", "readme", "contact", "notifications", "new", "codespaces", "issues", "pulls", "search", "apps", "solutions", "resources",
    )
    private val DOWNLOAD_EXT = Regex("""(?i)\.(apk|zip|rar|7z|dmg|pkg|exe|msi|deb|rpm|appimage|tar\.gz|tgz|xpi|crx|ipa)(\?.*)?$""")
    private val SHARE_PATTERNS = listOf("twitter.com/intent", "x.com/intent", "facebook.com/sharer", "service.weibo.com/share", "linkedin.com/sharing", "reddit.com/submit")

    data class Guess(val category: LinkCategory, val note: String = "", val isResource: Boolean = false)

    fun guess(resolved: String, location: String): Guess {
        val host = UrlTools.hostOf(resolved).removePrefix("www.")
        val path = UrlTools.pathOf(resolved)
        val segs = path.split('/').filter { it.isNotEmpty() }
        val lower = resolved.lowercase()
        if (SHARE_PATTERNS.any { lower.contains(it) }) return Guess(LinkCategory.AUX, "分享按钮链接")
        if (DOWNLOAD_EXT.containsMatchIn(path)) return Guess(LinkCategory.PENDING, "下载链接，未验证安全性与真实指向")
        val res = when {
            host == "github.com" && segs.size >= 2 && segs[0].lowercase() !in GITHUB_RESERVED -> true
            host == "gitlab.com" && segs.size >= 2 -> true
            host == "huggingface.co" && segs.isNotEmpty() -> true
            host == "chromewebstore.google.com" || (host == "chrome.google.com" && path.startsWith("/webstore")) -> true
            host == "addons.mozilla.org" || host == "microsoftedge.microsoft.com" -> true
            host == "play.google.com" && path.startsWith("/store/apps") -> true
            host == "apps.apple.com" -> true
            host == "npmjs.com" && path.startsWith("/package") -> true
            host == "pypi.org" && path.startsWith("/project") -> true
            host == "marketplace.visualstudio.com" -> true
            host.startsWith("docs.") || segs.firstOrNull()?.lowercase() in setOf("docs", "documentation") -> true
            else -> false
        }
        if (res) return Guess(LinkCategory.RESOURCE, isResource = true)
        return when (location) {
            LinkLocation.CONTENT, LinkLocation.JINA, LinkLocation.TEXT -> Guess(LinkCategory.AUX)
            else -> Guess(LinkCategory.AUX)
        }
    }

    /**
     * @param inputUrls 用户输入/分享中出现的 URL（原始字符串）；mainIndex 为用户确认的主链接。
     * @param extracted 网页或原文中提取到的链接。
     */
    fun build(
        inputUrls: List<String>,
        mainIndex: Int,
        extracted: List<ExtractedLink>,
        inputLocation: String = LinkLocation.INPUT,
    ): List<ClassifiedLink> {
        data class Acc(var link: ExtractedLink, var cat: LinkCategory, var note: String, var occ: Int, var send: Boolean)
        val byResolved = LinkedHashMap<String, Acc>()

        inputUrls.forEachIndexed { i, raw ->
            val key = raw.trim()
            if (key in byResolved) { byResolved[key]!!.occ++; return@forEachIndexed }
            val isMain = i == mainIndex && inputLocation == LinkLocation.INPUT
            val g = guess(raw, inputLocation)
            val cat = if (isMain) LinkCategory.SOURCE else if (inputLocation == LinkLocation.INPUT) LinkCategory.PENDING.takeIf { !g.isResource } ?: LinkCategory.RESOURCE else g.category
            byResolved[key] = Acc(ExtractedLink(raw, raw, "", inputLocation), cat,
                if (!isMain && inputLocation == LinkLocation.INPUT) "用户输入中的其他链接，请确认用途" else g.note, 1, true)
        }
        // 正文内链接优先于页面导航链接
        val ordered = extracted.sortedBy { if (it.location == LinkLocation.PAGE) 1 else 0 }
        for (e in ordered) {
            val key = e.resolved.ifBlank { e.raw }.trim()
            val existing = byResolved[key]
            if (existing != null) {
                existing.occ++
                if (existing.link.anchor.isBlank() && e.anchor.isNotBlank()) existing.link = existing.link.copy(anchor = e.anchor)
                if (e.location != LinkLocation.PAGE) existing.send = true
                continue
            }
            val g = guess(key, e.location)
            val send = e.location != LinkLocation.PAGE || g.isResource
            byResolved[key] = Acc(e, g.category, g.note, 1, send)
        }

        // 规范化后相同但原始写法不同：只提示，不合并。
        val firstByNorm = HashMap<String, String>()
        val out = ArrayList<ClassifiedLink>()
        var n = 0
        for ((key, a) in byResolved) {
            n++
            val id = "L$n"
            val norm = UrlTools.normalizeForMatch(key)
            var note = a.note
            firstByNorm[norm]?.let { other -> note = listOf(note, "与 $other 可能指向同一地址（写法不同，未合并）").filter { it.isNotBlank() }.joinToString("；") }
            firstByNorm.putIfAbsent(norm, id)
            out.add(
                ClassifiedLink(
                    linkId = id, raw = a.link.raw, resolved = key, normalized = norm, location = a.link.location,
                    category = a.cat, anchor = a.link.anchor, occurrences = a.occ, note = note, sendToModel = a.send,
                )
            )
        }
        return out
    }
}
