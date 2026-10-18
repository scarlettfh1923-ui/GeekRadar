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

    private val X_HOSTS = setOf("x.com", "twitter.com", "mobile.twitter.com", "mobile.x.com")
    private val X_RESERVED = setOf(
        "home", "explore", "search", "i", "intent", "settings", "notifications", "messages", "hashtag", "share", "login", "signup",
        "tos", "privacy", "compose", "jobs", "lists", "communities", "logout", "account", "download", "en", "about",
    )
    private val X_PROFILE_TABS = setOf("with_replies", "media", "likes", "followers", "following", "verified_followers", "highlights", "articles")

    /**
     * 识别"原发帖人"相关链接：社交平台 / 内容平台上的作者主页、个人资料页，以及帖子本身。
     * 返回说明文字；不是作者/帖子链接时返回 null。
     * 仅凭 URL 结构判断（不联网），宁可漏判也不把普通网站误判为作者主页。
     */
    fun authorOrPostKind(url: String): String? {
        val host = UrlTools.hostOf(url).removePrefix("www.").removePrefix("m.")
        val path = UrlTools.pathOf(url)
        val segs = path.split('/').filter { it.isNotEmpty() }
        val first = segs.firstOrNull()?.lowercase()
        val query = url.substringAfter('?', "").substringBefore('#')
        return when {
            host in X_HOSTS -> when {
                segs.isEmpty() || first in X_RESERVED -> null
                segs.size >= 2 && segs[1].lowercase() == "status" -> "社交平台帖子"
                segs.size == 1 || segs[1].lowercase() in X_PROFILE_TABS -> "作者主页"
                else -> null
            }
            host == "mp.weixin.qq.com" -> when {
                path.startsWith("/mp/profile_ext") || path.startsWith("/mp/homepage") || (path.startsWith("/mp/getmasssendmsg")) -> "公众号主页"
                else -> null
            }
            host == "weibo.com" || host == "weibo.cn" -> when {
                first == "u" || first == "n" -> "作者主页"
                segs.size == 1 && first !in setOf("hot", "login", "signup", "newlogin", "search") -> "作者主页"
                segs.size == 2 && first?.all { it.isDigit() } == true -> "社交平台帖子"
                first == "status" || first == "detail" -> "社交平台帖子"
                else -> null
            }
            host == "xiaohongshu.com" -> when {
                first == "user" -> "作者主页"
                first == "explore" || first == "discovery" -> "社交平台帖子"
                else -> null
            }
            host == "zhihu.com" && (first == "people" || first == "org") -> "作者主页"
            host == "space.bilibili.com" -> "作者主页"
            host == "youtube.com" && (first?.startsWith("@") == true || first in setOf("channel", "c", "user")) -> "作者主页"
            host == "medium.com" && first?.startsWith("@") == true && segs.size == 1 -> "作者主页"
            host == "juejin.cn" && first == "user" -> "作者主页"
            host == "linkedin.com" && (first == "in" || first == "company") -> "作者主页"
            host == "threads.net" || host == "threads.com" -> if (first?.startsWith("@") == true) (if (segs.size >= 3 && segs[1] == "post") "社交平台帖子" else "作者主页") else null
            host == "instagram.com" && segs.size == 1 && first !in setOf("explore", "accounts", "about", "reels") -> "作者主页"
            host == "facebook.com" && segs.size == 1 && first !in setOf("login", "sharer", "sharer.php", "help", "watch", "groups", "events") -> "作者主页"
            host == "douyin.com" && first == "user" -> "作者主页"
            host == "v2ex.com" && first == "member" -> "作者主页"
            host == "okjike.com" && first == "u" -> "作者主页"
            host == "web.okjike.com" && first == "u" -> "作者主页"
            host == "bsky.app" && first == "profile" -> if (segs.size >= 4 && segs[2] == "post") "社交平台帖子" else "作者主页"
            host == "t.me" && segs.size == 1 -> "作者主页"
            host == "space.bilibili.com" || (host == "bilibili.com" && first == "space") -> "作者主页"
            else -> if (query.contains("__biz=") && host.endsWith("weixin.qq.com") && path.contains("profile")) "公众号主页" else null
        }
    }

    /** 是否属于作者主页 / 帖子类链接（归为来源，不进文案）。 */
    fun isAuthorOrPost(url: String): Boolean = authorOrPostKind(url) != null

    fun guess(resolved: String, location: String): Guess {
        val host = UrlTools.hostOf(resolved).removePrefix("www.")
        val path = UrlTools.pathOf(resolved)
        val segs = path.split('/').filter { it.isNotEmpty() }
        val lower = resolved.lowercase()
        if (SHARE_PATTERNS.any { lower.contains(it) }) return Guess(LinkCategory.AUX, "分享按钮链接")
        authorOrPostKind(resolved)?.let { return Guess(LinkCategory.SOURCE, "$it · ${LinkCategory.SOURCE_NOTE}") }
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
        return Guess(LinkCategory.AUX)
    }

    /** 内容平台（用户分享的主链接在这些站点时，一定是"原帖 / 原文"，归为来源）。 */
    private val CONTENT_HOSTS = setOf(
        "x.com", "twitter.com", "mobile.twitter.com", "mp.weixin.qq.com", "weibo.com", "m.weibo.cn", "weibo.cn", "xiaohongshu.com", "xhslink.com",
        "zhihu.com", "zhuanlan.zhihu.com", "bilibili.com", "b23.tv", "youtube.com", "youtu.be", "medium.com", "juejin.cn", "reddit.com",
        "news.ycombinator.com", "threads.net", "threads.com", "linkedin.com", "facebook.com", "instagram.com", "douyin.com", "v2ex.com",
        "okjike.com", "m.okjike.com", "web.okjike.com", "bsky.app", "t.me", "substack.com", "jianshu.com", "csdn.net", "blog.csdn.net",
        "36kr.com", "sspai.com", "ifanr.com", "producthunt.com",
    )

    /**
     * 用户分享 / 粘贴的主链接如何分类：
     *  - 本身就是资源（GitHub 仓库、应用商店、文档等），或是非内容平台的网站首页 → 资源；
     *  - 其他（帖子、文章、公众号等）→ 来源：只在应用内保留，不发给模型、不进文案。
     */
    fun mainLinkGuess(url: String): Guess {
        val g = guess(url, LinkLocation.INPUT)
        if (g.category == LinkCategory.SOURCE) return g
        if (g.isResource) return Guess(LinkCategory.RESOURCE, "你分享的链接本身就是资源", isResource = true)
        val host = UrlTools.hostOf(url).removePrefix("www.").removePrefix("m.")
        val isContentHost = CONTENT_HOSTS.any { host == it || host.endsWith(".$it") }
        val path = UrlTools.pathOf(url).trim('/')
        val query = url.substringAfter('?', "")
        if (!isContentHost && path.isEmpty() && (query.isEmpty() || !url.contains('?'))) {
            return Guess(LinkCategory.RESOURCE, "你分享的网站首页本身就是资源", isResource = true)
        }
        return Guess(LinkCategory.SOURCE, "原始来源 · ${LinkCategory.SOURCE_NOTE}")
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
            val g = if (isMain) mainLinkGuess(raw) else guess(raw, inputLocation)
            val cat = when {
                isMain -> g.category
                g.category == LinkCategory.SOURCE -> LinkCategory.SOURCE
                inputLocation == LinkLocation.INPUT -> if (g.isResource) LinkCategory.RESOURCE else LinkCategory.PENDING
                else -> g.category
            }
            val note = if (!isMain && inputLocation == LinkLocation.INPUT && cat != LinkCategory.SOURCE) "用户输入中的其他链接，请确认用途" else g.note
            byResolved[key] = Acc(ExtractedLink(raw, raw, "", inputLocation), cat, note, 1, cat != LinkCategory.SOURCE)
        }
        // 正文内链接优先于页面导航链接
        val ordered = extracted.sortedBy { if (it.location == LinkLocation.PAGE) 1 else 0 }
        for (e in ordered) {
            val key = e.resolved.ifBlank { e.raw }.trim()
            val existing = byResolved[key]
            if (existing != null) {
                existing.occ++
                if (existing.link.anchor.isBlank() && e.anchor.isNotBlank()) existing.link = existing.link.copy(anchor = e.anchor)
                if (e.location != LinkLocation.PAGE && existing.cat != LinkCategory.SOURCE) existing.send = true
                continue
            }
            val g = guess(key, e.location)
            // 来源类（原帖 / 作者主页）永不发给模型。
            val send = g.category != LinkCategory.SOURCE && (e.location != LinkLocation.PAGE || g.isResource)
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
