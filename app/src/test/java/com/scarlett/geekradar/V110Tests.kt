package com.scarlett.geekradar

import com.scarlett.geekradar.core.*
import com.scarlett.geekradar.data.Backup
import com.scarlett.geekradar.data.LinkEntity
import com.scarlett.geekradar.data.Repository
import com.scarlett.geekradar.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test

/** 1.1.0：来源 / 作者链接隔离、提示词迁移、标签、安全网、封面排版。 */
class AuthorLinkTest {
    @Test fun detectsAuthorProfilesAndPosts() {
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://x.com/geekshare"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://twitter.com/geekshare/with_replies"))
        assertEquals("社交平台帖子", LinkClassifier.authorOrPostKind("https://x.com/geekshare/status/1847203391?s=46&t=Qm8Z"))
        assertEquals("公众号主页", LinkClassifier.authorOrPostKind("https://mp.weixin.qq.com/mp/profile_ext?action=home&__biz=MzA5&scene=124#wechat_redirect"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://weibo.com/u/123456"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://www.zhihu.com/people/abc"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://www.youtube.com/@somebody"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://www.xiaohongshu.com/user/profile/5f00"))
        assertEquals("作者主页", LinkClassifier.authorOrPostKind("https://space.bilibili.com/1234"))
        // 不是作者链接
        assertNull(LinkClassifier.authorOrPostKind("https://x.com/home"))
        assertNull(LinkClassifier.authorOrPostKind("https://x.com/search?q=ai"))
        assertNull(LinkClassifier.authorOrPostKind("https://x.com/intent/tweet?url=a"))
        assertNull(LinkClassifier.authorOrPostKind("https://github.com/browser-use/browser-use"))
        assertNull(LinkClassifier.authorOrPostKind("https://docs.browser-use.com/quickstart"))
        assertNull(LinkClassifier.authorOrPostKind("https://mp.weixin.qq.com/s?__biz=MzA5&mid=1&idx=1"))
        assertNull(LinkClassifier.authorOrPostKind("https://cursor.com"))
    }

    @Test fun mainLinkIsSourceUnlessItIsTheResource() {
        assertEquals(LinkCategory.SOURCE, LinkClassifier.mainLinkGuess("https://x.com/geekshare/status/1?s=20").category)
        assertEquals(LinkCategory.SOURCE, LinkClassifier.mainLinkGuess("https://mp.weixin.qq.com/s/AbCdEf").category)
        assertEquals(LinkCategory.SOURCE, LinkClassifier.mainLinkGuess("https://36kr.com/p/123").category)
        assertEquals(LinkCategory.SOURCE, LinkClassifier.mainLinkGuess("https://blog.example.com/2026/10/new-tool").category)
        assertEquals(LinkCategory.RESOURCE, LinkClassifier.mainLinkGuess("https://github.com/browser-use/browser-use").category)
        assertEquals(LinkCategory.RESOURCE, LinkClassifier.mainLinkGuess("https://cursor.com/").category)
        assertEquals(LinkCategory.RESOURCE, LinkClassifier.mainLinkGuess("https://chromewebstore.google.com/detail/abc").category)
    }

    @Test fun sourceAndAuthorLinksAreNotSentToModel() {
        val ex = listOf(
            ExtractedLink("https://x.com/geekshare", "https://x.com/geekshare", "@geekshare", LinkLocation.CONTENT),
            ExtractedLink("https://github.com/browser-use/browser-use", "https://github.com/browser-use/browser-use", "GitHub", LinkLocation.CONTENT),
            ExtractedLink("/someone", "https://x.com/someone", "Someone", LinkLocation.PAGE),
        )
        val out = LinkClassifier.build(listOf("https://x.com/geekshare/status/1847203391?s=46&t=Qm8Z"), 0, ex)
        val main = out[0]
        assertEquals(LinkCategory.SOURCE, main.category); assertFalse(main.sendToModel)
        assertEquals("https://x.com/geekshare/status/1847203391?s=46&t=Qm8Z", main.raw)  // 原样保留
        val prof = out.first { it.raw == "https://x.com/geekshare" }
        assertEquals(LinkCategory.SOURCE, prof.category); assertFalse(prof.sendToModel); assertTrue(prof.note.contains(LinkCategory.SOURCE_NOTE))
        assertFalse(out.first { it.raw == "/someone" }.sendToModel)
        val gh = out.first { it.raw.startsWith("https://github.com") }
        assertEquals(LinkCategory.RESOURCE, gh.category); assertTrue(gh.sendToModel)
        // 所有链接都保存在清单里
        assertEquals(4, out.size)
    }

    private fun entity(id: String, url: String, cat: LinkCategory, loc: String = LinkLocation.CONTENT, send: Boolean = true, confirmed: Boolean = false) =
        LinkEntity(taskId = 1, linkId = id, rawUrl = url, resolvedUrl = url, normalizedUrl = url, sourceLocation = loc, category = cat.name, sendToModel = send, userConfirmed = confirmed)

    @Test fun linksForModelExcludeSourceEvenForOldRecords() {
        val ls = listOf(
            entity("L1", "https://x.com/geekshare/status/1", LinkCategory.SOURCE, LinkLocation.INPUT, send = true),  // 1.0.0 记录：SOURCE 仍标记发送
            entity("L2", "https://x.com/geekshare", LinkCategory.AUX),                                              // 1.0.0 记录：作者主页被当作辅助
            entity("L3", "https://github.com/a/b", LinkCategory.RESOURCE),
            entity("L4", "https://x.com/friend", LinkCategory.RESOURCE, confirmed = true),                          // 用户手动确认为资源：尊重用户
        )
        assertEquals(listOf("L3", "L4"), Repository.linksForModel(ls).map { it.linkId })
        val input = PromptBuilder.Input("Geek on X: \"让 AI 替你点网页\" / X", "", "", "正文", Repository.linksForModel(ls).map {
            PromptBuilder.LinkForModel(it.linkId, it.rawUrl, LinkCategory.fromName(it.category), "", it.sourceLocation) }, "种草分享", "中")
        val msg = PromptBuilder.userMessage(input)
        assertFalse(msg.contains("x.com/geekshare")); assertFalse(msg.contains("原始来源链接")); assertFalse(msg.contains("Geek on X"))
        assertTrue(msg.contains("让 AI 替你点网页"))
    }

    @Test fun reclassifiesOldLinksOnUpgrade() {
        val prof = Repository.reclassifiedV2(entity("L2", "https://x.com/geekshare", LinkCategory.AUX))!!
        assertEquals("SOURCE", prof.category); assertFalse(prof.sendToModel); assertEquals("https://x.com/geekshare", prof.rawUrl)
        val oldMain = Repository.reclassifiedV2(entity("L1", "https://x.com/u/status/1", LinkCategory.SOURCE, LinkLocation.INPUT, send = true))!!
        assertEquals("SOURCE", oldMain.category); assertFalse(oldMain.sendToModel)
        val ghMain = Repository.reclassifiedV2(entity("L1", "https://github.com/a/b", LinkCategory.SOURCE, LinkLocation.INPUT, send = true))!!
        assertEquals("RESOURCE", ghMain.category); assertTrue(ghMain.sendToModel)
        assertNull(Repository.reclassifiedV2(entity("L3", "https://github.com/a/b", LinkCategory.RESOURCE)))
        assertNull(Repository.reclassifiedV2(entity("L4", "https://x.com/friend", LinkCategory.RESOURCE, confirmed = true)))
    }
}

class ComposerV110Test {
    private val views = listOf(
        Composer.LinkView("L1", "https://x.com/geekshare/status/1847203391?s=46&t=Qm8Z", "https://x.com/geekshare/status/1847203391?s=46&t=Qm8Z", LinkCategory.SOURCE, "", false),
        Composer.LinkView("L2", "https://github.com/browser-use/browser-use", "https://github.com/browser-use/browser-use", LinkCategory.RESOURCE, "browser-use GitHub 仓库", true),
        Composer.LinkView("L3", "https://docs.browser-use.com/quickstart", "https://docs.browser-use.com/quickstart", LinkCategory.RESOURCE, "官方文档", true),
        Composer.LinkView("L4", "https://x.com/geekshare", "https://x.com/geekshare", LinkCategory.SOURCE, "", false),
        Composer.LinkView("L5", "http://example-mirror.cn/bu_v0.5.zip?t=17%3A02", "http://example-mirror.cn/bu_v0.5.zip?t=17%3A02", LinkCategory.PENDING, "", true),
    )

    @Test fun fullCopyHasTagsAndNoSourceLinks() {
        val full = Composer.composeFull("标题", "项目地址见 [L2]", views, listOf("AI工具", "开源"))
        assertTrue(full.contains("项目地址见 https://github.com/browser-use/browser-use"))
        assertTrue(full.contains("browser-use GitHub 仓库：https://github.com/browser-use/browser-use"))
        assertTrue(full.contains("官方文档：https://docs.browser-use.com/quickstart"))
        assertTrue(full.trimEnd().endsWith("#AI工具 #开源"))
        assertFalse(full.contains("x.com"))
    }

    @Test fun allLinksCopyExcludesSource() {
        val all = Composer.allLinksText(views)
        assertFalse(all.contains("x.com"))
        assertTrue(all.contains("https://github.com/browser-use/browser-use"))
        assertTrue(all.contains("http://example-mirror.cn/bu_v0.5.zip?t=17%3A02"))  // 原样，未改 https、未去参数
    }

    @Test fun safetyNetFlagsSourceMentionsWithoutChangingText() {
        val body = "工具很好用。原帖见 [L1]，作者主页见 https://x.com/geekshare ，感谢 @geekshare"
        val w = Composer.sourceMentionWarnings(listOf("标题", body), views)
        assertEquals(3, w.size)
        assertTrue(w[0].contains("L1") && w[0].contains("L4"))
        assertTrue(w[1].contains("原帖") && w[1].contains("作者主页"))
        assertTrue(w[2].contains("@geekshare"))
        assertTrue(Composer.sourceMentionWarnings(listOf("让 AI 替你点网页", "项目地址见 [L2]，邮箱 a@b.com"), views).isEmpty())
    }
}

class TagsTest {
    @Test fun normalizeParseMerge() {
        assertEquals("AI工具", Tags.normalize(" #AI工具 "))
        assertNull(Tags.normalize(" # "))
        assertEquals(listOf("AI工具", "效率神器", "开源"), Tags.parseInput("#AI工具 效率神器，#开源 #ai工具"))
        assertEquals(listOf("A", "B", "C"), Tags.merge(listOf("A", "B"), listOf("b", "C")))
        assertEquals("#A #B", Tags.line(listOf("A", "#B")))
        assertEquals(listOf("A", "B"), Tags.fromJson(Tags.toJson(listOf("A", "B"))))
        assertEquals(emptyList<String>(), Tags.fromJson("not json"))
    }

    @Test fun extractsTrailingHashtagLinesOnly() {
        val (body, tags) = Tags.extractTrailing("第一段 #不是标签行\n第二段\n\n#AI工具 #开源\n#效率神器\n")
        assertEquals("第一段 #不是标签行\n第二段", body)
        assertEquals(listOf("AI工具", "开源", "效率神器"), tags)
        assertEquals("没有标签" to emptyList<String>(), Tags.extractTrailing("没有标签"))
    }

    @Test fun parserReadsTagsFieldAndOldStyleTrailingTags() {
        val p = OutputParser.parse("{\"titles\":[\"A\"],\"body\":\"正文\",\"tags\":[\"#AI工具\",\"开源 项目\"]}")
        assertEquals(listOf("AI工具", "开源", "项目"), p.tags); assertEquals("正文", p.body)
        val old = OutputParser.parse("{\"titles\":[\"A\"],\"body\":\"正文\\n\\n#AI工具 #开源\"}")
        assertEquals("正文", old.body); assertEquals(listOf("AI工具", "开源"), old.tags)
    }
}

class PromptMigrationTest {
    @Test fun legacyDefaultMigratesCustomStays() {
        assertEquals(DefaultPrompt.TEXT, DefaultPrompt.migrationTarget(DefaultPrompt.LEGACY_1_0_0, 1))
        assertEquals(DefaultPrompt.TEXT, DefaultPrompt.migrationTarget(DefaultPrompt.LEGACY_1_0_0 + "\n", 1))  // 首尾空白不影响判断
        assertNull(DefaultPrompt.migrationTarget(DefaultPrompt.LEGACY_1_0_0 + "\n再加一条我的要求", 1))       // 已自定义：不动
        assertNull(DefaultPrompt.migrationTarget("完全自定义的提示词", 1))
        assertNull(DefaultPrompt.migrationTarget(DefaultPrompt.LEGACY_1_0_0, DefaultPrompt.REVISION))       // 已迁移过：不再执行
        assertNull(DefaultPrompt.migrationTarget(DefaultPrompt.TEXT, 1))
    }

    @Test fun newDefaultForbidsAuthorMentionsAndAsksForTags() {
        val t = DefaultPrompt.TEXT
        assertTrue(t.contains("不提原发帖人")); assertTrue(t.contains("原帖见")); assertTrue(t.contains("作者主页见"))
        assertTrue(t.contains("搬运分享者")); assertTrue(t.contains("不要编造亲身体验")); assertTrue(t.contains("\"tags\""))
        assertNotEquals(DefaultPrompt.LEGACY_1_0_0, t)
        assertTrue(DefaultPrompt.LEGACY_1_0_0.startsWith("你是一名严谨又有亲和力的中文科技内容编辑，擅长把科技工具"))
    }
}

class BackupV110Test {
    @Test fun tagsRoundTripAndV1BackupStillImports() {
        val t = TaskEntity(id = 1, uuid = "u-2", createdAt = 1, updatedAt = 2, inputType = "url", rawInput = "x", tagsJson = "[\"AI工具\",\"开源\"]", generatedTagsJson = "[\"AI工具\"]")
        val d = Backup.parse(Backup.export(listOf(t), emptyList(), "P", 1, emptyList(), emptyMap(), "1.1.0"))
        assertEquals(listOf("AI工具", "开源"), Tags.fromJson(d.tasks[0].first.tagsJson))
        assertEquals(listOf("AI工具"), Tags.fromJson(d.tasks[0].first.generatedTagsJson))
        val v1 = "{\"format\":\"geekradar-backup\",\"version\":1,\"tasks\":[{\"uuid\":\"old\",\"rawInput\":\"a\",\"finalBody\":\"旧正文\",\"links\":[]}]}"
        val old = Backup.parse(v1).tasks[0].first
        assertEquals("旧正文", old.finalBody); assertEquals("[]", old.tagsJson)
    }
}

class CoverLayoutTest {
    private val measure: (String) -> Float = { s -> s.sumOf { if (it.code < 128) 1.0 else 2.0 }.toFloat() }

    @Test fun wrapsCjkAndKeepsLatinWordsTogether() {
        val lines = CoverLayout.wrap("让 AI 替你点网页", 6f, measure)
        assertEquals(listOf("让 AI", "替你点", "网页"), lines)
        assertEquals(listOf("browser-use"), CoverLayout.wrap("browser-use", 20f, measure))
        // 行首禁则：逗号不放在行首
        assertTrue(CoverLayout.wrap("一二三，四五", 6f, measure).none { it.startsWith("，") })
        // 超长单词按字符断开
        assertTrue(CoverLayout.wrap("abcdefghij", 4f, measure).all { measure(it) <= 4f })
    }

    @Test fun fitTitlePicksLargestSizeWithinLines() {
        val (size, lines) = CoverLayout.fitTitle("让 AI 替你点网页", 12f, listOf(3f, 2f, 1f), 4) { s, sz -> measure(s) * sz }
        assertTrue(lines.size <= 4); assertEquals(2f, size)
    }

    @Test fun balancedTitleAvoidsOrphanLastLine() {
        val greedy = CoverLayout.wrap("GitHub 爆火！让 AI 替你点网页", 13f, measure)
        val bal = CoverLayout.balance("GitHub 爆火！让 AI 替你点网页", 13f, greedy.size, measure)
        assertEquals(greedy.size, bal.size)
        assertTrue(measure(bal.last()) >= 4f)                               // 最后一行不止一个字
        assertTrue(bal.maxOf(measure) <= greedy.maxOf(measure))
        assertEquals("GitHub 爆火！让 AI 替你点网页".replace(" ", ""), bal.joinToString("").replace(" ", ""))  // 不丢字
    }

    @Test fun contentHasNoAuthorInfoOrLinks() {
        val c = CoverLayout.content(27, "让 AI 替你点网页 🔥", "最近发现一个开源工具 browser-use，项目见 [L2] https://x.com/geekshare 。\n更多", listOf("开源工具", "GitHub开源", "免费"))
        assertEquals("No.27", c.number); assertEquals("开源工具推荐", c.kicker); assertEquals("让 AI 替你点网页", c.title)
        assertFalse(c.subtitle.contains("[L2]")); assertFalse(c.subtitle.contains("x.com"))
        assertEquals("GitHub开源 · 免费", c.footerLeft)
    }
}
