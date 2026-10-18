package com.scarlett.geekradar.data

import androidx.room.withTransaction
import com.scarlett.geekradar.core.ClassifiedLink
import com.scarlett.geekradar.core.Composer
import com.scarlett.geekradar.core.DeepSeekClient
import com.scarlett.geekradar.core.DefaultPrompt
import com.scarlett.geekradar.core.ExtractedLink
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.core.LinkClassifier
import com.scarlett.geekradar.core.LinkLocation
import com.scarlett.geekradar.core.OutputParser
import com.scarlett.geekradar.core.PageFetcher
import com.scarlett.geekradar.core.PromptBuilder
import com.scarlett.geekradar.core.UrlTools
import org.json.JSONArray
import java.util.UUID

/** 业务编排：任务创建、抓取、链接保存、生成、回填、提示词与备份。 */
class Repository(
    val db: AppDatabase,
    val settings: SettingsStore,
    val keys: SecureKeyStore,
    private val fetcher: PageFetcher = PageFetcher(jinaKeyProvider = { keys.jinaKey }),
    private val api: DeepSeekClient = DeepSeekClient(),
) {
    val tasks = db.tasks()
    val links = db.links()
    val prompts = db.prompts()

    suspend fun ensurePromptInitialized() {
        if (!settings.promptInitialized) {
            settings.prompt = DefaultPrompt.TEXT
            settings.promptVersion = 1
            settings.promptUpdatedAt = System.currentTimeMillis()
            prompts.insert(PromptSnapshotEntity(version = 1, content = DefaultPrompt.TEXT, createdAt = System.currentTimeMillis(), label = "默认提示词"))
            settings.promptInitialized = true
        }
        tasks.markInterrupted()
    }

    // ---------------- 任务创建与抓取 ----------------

    /** 创建任务。articleMode=true 表示用户粘贴的是原文（其中链接作为原文链接保存）。 */
    suspend fun createTask(rawInput: String, inputType: String, mainUrl: String?, articleMode: Boolean, style: String, length: String): Long {
        val now = System.currentTimeMillis()
        val t = TaskEntity(
            uuid = UUID.randomUUID().toString(), createdAt = now, updatedAt = now,
            inputType = if (articleMode) "text" else inputType, rawInput = rawInput,
            originalUrl = if (articleMode) null else mainUrl,
            sourceText = if (articleMode) rawInput.trim() else "",
            fetchStatus = if (articleMode) "MANUAL" else "NONE", fetchChannel = if (articleMode) "manual" else "none",
            style = style, length = length,
            pageTitle = "",
        )
        val id = tasks.insert(t)
        val task = tasks.get(id)!!
        links.replaceForTask(id, toEntities(id, buildLinks(task, emptyList()), emptyList()))
        return id
    }

    /** 根据任务的原始输入 + 抓取结果（+ 粘贴原文中的链接）重建链接清单。 */
    private fun buildLinks(task: TaskEntity, fetched: List<ExtractedLink>): List<ClassifiedLink> {
        val inputUrls = UrlTools.analyze(task.rawInput).urls
        val textLinks = UrlTools.extractUrls(task.sourceText).map { ExtractedLink(it.raw, it.raw, "", LinkLocation.TEXT) }
        return if (task.inputType == "text") {
            val main = task.originalUrl
            val extraText = UrlTools.extractUrls(task.rawInput).map { ExtractedLink(it.raw, it.raw, "", LinkLocation.TEXT) }
            LinkClassifier.build(listOfNotNull(main), 0, extraText + fetched + textLinks)
        } else {
            val main = inputUrls.indexOf(task.originalUrl).coerceAtLeast(0)
            LinkClassifier.build(inputUrls, main, fetched + textLinks)
        }
    }

    /** 新清单入库，保留用户对同一链接（按解析后地址匹配）的确认、备注和 AI 给出的名称用途。 */
    private fun toEntities(taskId: Long, list: List<ClassifiedLink>, old: List<LinkEntity>): List<LinkEntity> {
        val oldBy = old.associateBy { it.resolvedUrl }
        return list.mapIndexed { i, c ->
            val o = oldBy[c.resolved]
            LinkEntity(
                taskId = taskId, linkId = c.linkId, rawUrl = c.raw, resolvedUrl = c.resolved, normalizedUrl = c.normalized,
                sourceLocation = c.location,
                category = if (o != null && o.userConfirmed) o.category else c.category.name,
                anchorText = c.anchor, name = o?.name ?: "", purpose = o?.purpose ?: "", userNote = o?.userNote ?: "",
                programNote = c.note, occurrences = c.occurrences, sendToModel = c.sendToModel || (o?.userConfirmed == true),
                userConfirmed = o?.userConfirmed ?: false, position = i,
            )
        }
    }

    data class FetchReport(val ok: Boolean, val message: String)

    suspend fun fetch(taskId: Long, overrideUrl: String? = null): FetchReport {
        var task = tasks.get(taskId) ?: return FetchReport(false, "任务不存在")
        val url = overrideUrl ?: task.originalUrl ?: return FetchReport(false, "没有可抓取的链接")
        if (overrideUrl != null && overrideUrl != task.originalUrl) {
            task = task.copy(originalUrl = overrideUrl); tasks.update(task)
        }
        val out = fetcher.fetch(url, settings.jinaEnabled)
        task = tasks.get(taskId) ?: return FetchReport(false, "任务不存在")
        val replaceText = !task.sourceEdited || task.sourceText.isBlank()
        val statusStr = out.status.name
        val errParts = listOf(out.error, out.localError.takeIf { out.channel == "jina" }?.let { "本地：$it" },
            out.jinaError.takeIf { it.isNotBlank() }?.let { it }).filterNotNull().filter { it.isNotBlank() }.distinct()
        val updated = task.copy(
            updatedAt = System.currentTimeMillis(), fetchedAt = System.currentTimeMillis(),
            finalPageUrl = out.finalUrl,
            pageTitle = out.title.ifBlank { task.pageTitle },
            pageDescription = out.description.ifBlank { task.pageDescription },
            sourceText = if (replaceText && out.text.isNotBlank()) out.text else task.sourceText,
            sourceEdited = if (replaceText && out.text.isNotBlank()) false else task.sourceEdited,
            fetchStatus = if (out.status == PageFetcher.Status.FAILED && task.sourceEdited && task.sourceText.isNotBlank()) "MANUAL" else statusStr,
            fetchChannel = if (out.status == PageFetcher.Status.FAILED) (if (task.sourceEdited) "manual" else "none") else out.channel,
            fetchError = errParts.joinToString("；"),
        )
        tasks.update(updated)
        val old = links.forTask(taskId)
        links.replaceForTask(taskId, toEntities(taskId, buildLinks(updated, out.links), old))
        return when (out.status) {
            PageFetcher.Status.OK -> FetchReport(true, if (out.channel == "jina") "已通过备用读取通道获取内容" else "已获取网页内容")
            PageFetcher.Status.PARTIAL -> FetchReport(true, "只获取到部分内容，可补充粘贴原文")
            PageFetcher.Status.FAILED -> FetchReport(false, FETCH_FAIL)
        }
    }

    suspend fun updateSource(taskId: Long, text: String) {
        val t = tasks.get(taskId) ?: return
        if (t.sourceText == text) return
        val manual = t.fetchStatus in setOf("FAILED", "NONE", "PARTIAL", "MANUAL")
        tasks.update(t.copy(sourceText = text, sourceEdited = true, updatedAt = System.currentTimeMillis(),
            fetchStatus = if (manual && text.isNotBlank()) "MANUAL" else t.fetchStatus,
            fetchChannel = if (manual && text.isNotBlank()) "manual" else t.fetchChannel))
    }

    /** 把粘贴原文中新出现的 URL 追加进链接清单（已有编号保持不变）。 */
    suspend fun syncTextLinks(taskId: Long) {
        val t = tasks.get(taskId) ?: return
        val existing = links.forTask(taskId)
        val known = existing.flatMap { listOf(it.rawUrl, it.resolvedUrl) }.toHashSet()
        val fresh = UrlTools.extractUrls(t.sourceText).map { it.raw }.filter { it !in known }
        if (fresh.isEmpty()) return
        var n = existing.mapNotNull { it.linkId.removePrefix("L").toIntOrNull() }.maxOrNull() ?: 0
        val add = fresh.mapIndexed { i, raw ->
            n++
            val g = LinkClassifier.guess(raw, LinkLocation.TEXT)
            LinkEntity(taskId = taskId, linkId = "L$n", rawUrl = raw, resolvedUrl = raw, normalizedUrl = UrlTools.normalizeForMatch(raw),
                sourceLocation = LinkLocation.TEXT, category = g.category.name, programNote = g.note, position = existing.size + i)
        }
        links.insertAll(add)
    }

    suspend fun updateTask(t: TaskEntity) = tasks.update(t.copy(updatedAt = System.currentTimeMillis()))
    suspend fun updateLink(l: LinkEntity) = links.update(l)

    // ---------------- 生成 ----------------

    data class GenOutcome(val ok: Boolean, val message: String)

    private fun linkViews(ls: List<LinkEntity>) = ls.map {
        Composer.LinkView(it.linkId, it.rawUrl, it.resolvedUrl, LinkCategory.fromName(it.category), it.name, it.sendToModel)
    }

    fun userNoteOf(t: TaskEntity): String {
        if (t.inputType == "text") return ""
        var rest = t.rawInput
        UrlTools.extractUrls(rest).forEach { rest = rest.replace(it.raw, " ") }
        return rest.trim()
    }

    fun buildInput(t: TaskEntity, ls: List<LinkEntity>): PromptBuilder.Input = PromptBuilder.Input(
        pageTitle = t.pageTitle, pageDescription = t.pageDescription,
        sourceLinkId = ls.firstOrNull { it.category == LinkCategory.SOURCE.name }?.linkId,
        userNote = userNoteOf(t), text = t.sourceText,
        links = ls.filter { it.sendToModel }.map { PromptBuilder.LinkForModel(it.linkId, it.rawUrl, LinkCategory.fromName(it.category), it.anchorText, it.sourceLocation) },
        style = t.style, length = t.length, maxChars = settings.maxChars,
    )

    suspend fun generate(taskId: Long, keepEdits: Boolean, promptOverride: String? = null): GenOutcome {
        syncTextLinks(taskId)
        val task = tasks.get(taskId) ?: return GenOutcome(false, "任务不存在")
        // 在开始时固定设置快照：生成过程中修改设置不影响本次请求。
        val prompt = promptOverride ?: settings.prompt
        val promptVersion = settings.promptVersion
        val cfg = DeepSeekClient.Config(settings.baseUrl, keys.apiKey, settings.model, settings.temperature.toDouble())
        if (prompt.isBlank()) return fail(task, "提示词为空，请在设置中编辑或恢复默认提示词")
        val ls = links.forTask(taskId)
        val input = buildInput(task, ls)
        if (input.text.isBlank() && input.userNote.isBlank()) return fail(task, "还没有可用的原文：请重试抓取，或直接粘贴原文后再生成")
        tasks.update(task.copy(genStatus = "RUNNING", genError = "", updatedAt = System.currentTimeMillis()))
        val res = api.chat(cfg, PromptBuilder.systemMessage(prompt), PromptBuilder.userMessage(input))
        val cur = tasks.get(taskId) ?: return GenOutcome(false, "任务已被删除")
        return when (res) {
            is DeepSeekClient.Result.Failure -> {
                tasks.update(cur.copy(genStatus = "ERROR", genError = res.message, updatedAt = System.currentTimeMillis()))
                GenOutcome(false, res.message)
            }
            is DeepSeekClient.Result.Success -> {
                val parsed = OutputParser.parse(res.content)
                val curLinks = links.forTask(taskId)
                val byId = curLinks.associateBy { it.linkId }
                // 模型建议只按内部 ID 关联；URL 从不由模型提供。
                val updatedLinks = parsed.suggestions.mapNotNull { s ->
                    val l = byId[s.id] ?: return@mapNotNull null
                    val newCat = s.category?.takeIf { !l.userConfirmed && l.category != LinkCategory.SOURCE.name && it != LinkCategory.SOURCE }
                    l.copy(name = s.name.ifBlank { l.name }, purpose = s.purpose.ifBlank { l.purpose },
                        category = newCat?.name ?: l.category, categoryByAi = newCat != null || l.categoryByAi)
                }
                if (updatedLinks.isNotEmpty()) links.updateAll(updatedLinks)
                val warnings = ArrayList<String>()
                parsed.problem?.let { warnings.add(it) }
                if (res.finishReason == "length") warnings.add("模型输出达到长度上限被截断，内容可能不完整")
                warnings.addAll(Composer.checkLinks(parsed.titles + parsed.body, linkViews(curLinks), parsed.suggestions.map { it.id }))
                val overwrite = !keepEdits || !cur.userEdited
                val newTitles = parsed.titles
                tasks.update(cur.copy(
                    titleCandidatesJson = JSONArray(newTitles).toString(),
                    generatedBody = parsed.body, rawModelOutput = res.content, structuredOk = parsed.structured,
                    factChecksJson = JSONArray(parsed.factChecks).toString(), uncertainJson = JSONArray(parsed.uncertain).toString(),
                    warningsJson = JSONArray(warnings).toString(),
                    genStatus = "DONE", genError = "", generatedAt = System.currentTimeMillis(),
                    modelName = res.model.ifBlank { cfg.model }, promptVersion = if (promptOverride != null) -1 else promptVersion, promptSnapshot = prompt,
                    finalTitle = if (overwrite) (newTitles.firstOrNull() ?: cur.finalTitle.ifBlank { cur.pageTitle }) else cur.finalTitle,
                    finalBody = if (overwrite) parsed.body else cur.finalBody,
                    selectedTitle = if (overwrite) 0 else cur.selectedTitle,
                    userEdited = if (overwrite) false else cur.userEdited,
                    pendingBody = if (overwrite) "" else parsed.body,
                    updatedAt = System.currentTimeMillis(),
                ))
                GenOutcome(true, if (parsed.problem == null) "生成完成" else "生成完成，但：${parsed.problem}")
            }
        }
    }

    private suspend fun fail(t: TaskEntity, msg: String): GenOutcome {
        tasks.update(t.copy(genStatus = "ERROR", genError = msg, updatedAt = System.currentTimeMillis()))
        return GenOutcome(false, msg)
    }

    /** 测试提示词（可为尚未保存的编辑版本），不写入任何记录。 */
    suspend fun testPrompt(promptText: String, taskId: Long?): Pair<DeepSeekClient.Result, OutputParser.Parsed?> {
        val cfg = DeepSeekClient.Config(settings.baseUrl, keys.apiKey, settings.model, settings.temperature.toDouble())
        val t = taskId?.let { tasks.get(it) }?.takeIf { it.sourceText.isNotBlank() }
        val input = if (t != null) buildInput(t, links.forTask(t.id)) else SAMPLE_INPUT
        val r = api.chat(cfg, PromptBuilder.systemMessage(promptText), PromptBuilder.userMessage(input))
        return r to (r as? DeepSeekClient.Result.Success)?.let { OutputParser.parse(it.content) }
    }

    suspend fun testConnection(baseUrl: String, model: String, key: String) =
        api.testConnection(DeepSeekClient.Config(baseUrl, key, model))

    // ---------------- 提示词 ----------------

    suspend fun savePrompt(text: String, label: String = "") {
        val v = settings.promptVersion + 1
        settings.prompt = text
        settings.promptVersion = v
        settings.promptUpdatedAt = System.currentTimeMillis()
        prompts.insert(PromptSnapshotEntity(version = v, content = text, createdAt = System.currentTimeMillis(), label = label))
    }

    /** 恢复默认：只改提示词（作为新版本保存），不动历史记录与 API 配置。 */
    suspend fun restoreDefaultPrompt() = savePrompt(DefaultPrompt.TEXT, "恢复默认")

    // ---------------- 备份 ----------------

    suspend fun exportJson(appVersion: String): String = Backup.export(
        tasks.all(), links.all(), settings.prompt, settings.promptVersion, prompts.all(), settings.exportable(), appVersion,
    )

    data class ImportPreview(val data: Backup.Data, val newCount: Int, val dupCount: Int)

    suspend fun previewImport(text: String): ImportPreview {
        val d = Backup.parse(text)
        var dup = 0
        d.tasks.forEach { (t, _) -> if (tasks.byUuid(t.uuid) != null) dup++ }
        return ImportPreview(d, d.tasks.size - dup, dup)
    }

    /** 在单个事务中导入；出错则整体回滚，不破坏现有数据。重复 uuid 的记录跳过。 */
    suspend fun applyImport(p: ImportPreview, importPrompt: Boolean, importSettings: Boolean): Int {
        var added = 0
        db.withTransaction {
            for ((t, ls) in p.data.tasks) {
                if (tasks.byUuid(t.uuid) != null) continue
                val id = tasks.insert(t.copy(id = 0))
                links.insertAll(ls.map { it.copy(id = 0, taskId = id) })
                added++
            }
            for (s in p.data.snapshots) if (prompts.find(s.version, s.content) == null) prompts.insert(s.copy(id = 0, label = s.label.ifBlank { "导入" }))
        }
        if (importPrompt && p.data.prompt != null && p.data.prompt != settings.prompt) savePrompt(p.data.prompt, "从备份导入")
        if (importSettings) p.data.settings?.let { s ->
            s.optString("baseUrl").takeIf { it.isNotBlank() }?.let { settings.baseUrl = it }
            s.optString("model").takeIf { it.isNotBlank() }?.let { settings.model = it }
            if (s.has("jinaEnabled")) settings.jinaEnabled = s.optBoolean("jinaEnabled")
            if (s.has("maxChars")) settings.maxChars = s.optInt("maxChars", settings.maxChars)
            if (s.has("temperature")) settings.temperature = s.optDouble("temperature", 0.7).toFloat()
            s.optString("defaultStyle").takeIf { it.isNotBlank() }?.let { settings.defaultStyle = it }
            s.optString("defaultLength").takeIf { it.isNotBlank() }?.let { settings.defaultLength = it }
        }
        return added
    }

    companion object {
        const val FETCH_FAIL = "暂时无法自动获取网页内容，你可以直接粘贴原文，继续生成文案。"

        val SAMPLE_INPUT = PromptBuilder.Input(
            pageTitle = "browser-use: Make websites accessible for AI agents",
            pageDescription = "开源 Python 库，让大模型操作真实浏览器",
            sourceLinkId = "L1", userNote = "",
            text = "browser-use is an open-source Python library that connects LLM agents to a real browser. " +
                "Agents can open pages, click buttons, fill forms and extract data. Install with pip install browser-use, " +
                "then configure your model API key. A hosted cloud version is also available. Docs cover quickstart and examples.",
            links = listOf(
                PromptBuilder.LinkForModel("L1", "https://github.com/browser-use/browser-use", LinkCategory.SOURCE, "", LinkLocation.INPUT),
                PromptBuilder.LinkForModel("L2", "https://cloud.browser-use.com/?ref=gh_readme&lang=zh-CN", LinkCategory.AUX, "Cloud", LinkLocation.CONTENT),
                PromptBuilder.LinkForModel("L3", "https://docs.browser-use.com/quickstart", LinkCategory.RESOURCE, "Docs", LinkLocation.CONTENT),
            ),
            style = "种草分享", length = "短",
        )
    }
}
