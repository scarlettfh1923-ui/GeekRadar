package com.scarlett.geekradar.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scarlett.geekradar.BuildConfig
import com.scarlett.geekradar.GeekRadarApp
import com.scarlett.geekradar.core.Composer
import com.scarlett.geekradar.core.DeepSeekClient
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.core.PromptBuilder
import com.scarlett.geekradar.core.UrlTools
import com.scarlett.geekradar.data.LinkEntity
import com.scarlett.geekradar.data.Repository
import com.scarlett.geekradar.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面路由。 */
data class Route(val name: String, val taskId: Long = 0) {
    fun encode() = "$name:$taskId"
    companion object {
        const val HOME = "home"; const val PROCESS = "process"; const val RESULT = "result"
        const val HISTORY = "history"; const val SETTINGS = "settings"; const val PROMPT = "prompt"
        fun decode(s: String) = s.split(':').let { Route(it[0], it.getOrNull(1)?.toLongOrNull() ?: 0) }
        val TOP = setOf(HOME, HISTORY, SETTINGS)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class AppViewModel(app: Application, private val ssh: SavedStateHandle) : AndroidViewModel(app) {
    val repo: Repository = (app as GeekRadarApp).repo
    private val st get() = repo.settings

    // ---------------- 导航（进程回收后可从 SavedStateHandle 恢复） ----------------
    val stack = mutableStateListOf<Route>().apply {
        val saved = ssh.get<String>("stack")
        if (saved.isNullOrBlank()) add(Route(Route.HOME)) else saved.split('|').forEach { add(Route.decode(it)) }
    }
    private fun persistStack() { ssh["stack"] = stack.joinToString("|") { it.encode() } }
    val current: Route get() = stack.last()
    fun push(r: Route) { if (current != r) stack.add(r); persistStack() }
    fun replaceTop(r: Route) { stack[stack.lastIndex] = r; persistStack() }
    fun pop(): Boolean { if (stack.size <= 1) return false; stack.removeAt(stack.lastIndex); persistStack(); return true }
    fun switchTab(name: String) { stack.clear(); stack.add(Route(Route.HOME)); if (name != Route.HOME) stack.add(Route(name)); persistStack() }

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    fun toast(s: String) { toasts.tryEmit(s) }

    init {
        viewModelScope.launch { repo.ensurePromptInitialized(); refreshSettingsState() }
    }

    // ---------------- 首页输入 ----------------
    var homeInput by mutableStateOf(ssh.get<String>("homeInput") ?: "")
        private set
    fun setHome(v: String) { homeInput = v; ssh["homeInput"] = v; if (homeMainIndex >= UrlTools.analyze(v).urls.size) homeMainIndex = 0 }
    var homeMainIndex by mutableIntStateOf(0)
    var homeFromShare by mutableStateOf(false)
    var homeNotice by mutableStateOf<String?>(null)
    var style by mutableStateOf(st.defaultStyle)
    var length by mutableStateOf(st.defaultLength)

    fun onShared(text: String?, subject: String?) {
        val t = text?.trim().orEmpty()
        val s = subject?.trim().orEmpty()
        if (t.isEmpty() && s.isEmpty()) { toast("分享内容为空，没有收到可识别的文字或链接"); return }
        // 同时保留分享的标题/附言和链接，不丢弃原始文本
        val combined = when { t.isEmpty() -> s; s.isEmpty() || t.contains(s) -> t; else -> "$s\n$t" }
        flushEditorNow()
        stack.clear(); stack.add(Route(Route.HOME)); persistStack()
        setHome(combined); homeMainIndex = 0; homeFromShare = true
        val a = UrlTools.analyze(combined)
        if (a.urls.size > 1 && !a.looksLikeArticle) {
            homeNotice = "分享内容中包含 ${a.urls.size} 个链接，请确认要解析的主链接，其余链接也会一并保存"
            return
        }
        homeNotice = null
        start()
    }

    /** 创建任务并进入处理页；URL 模式下自动开始抓取。 */
    fun start() {
        val input = homeInput
        val a = UrlTools.analyze(input)
        if (a.kind == UrlTools.InputKind.EMPTY) { toast("请先粘贴链接或原文"); return }
        val article = a.urls.isEmpty() || a.looksLikeArticle
        val main = a.urls.getOrNull(homeMainIndex) ?: a.urls.firstOrNull()
        val type = if (homeFromShare) "share" else if (article) "text" else "url"
        viewModelScope.launch {
            val id = repo.createTask(input, type, main, article, style, length)
            setHome(""); homeFromShare = false; homeNotice = null; homeMainIndex = 0
            push(Route(Route.PROCESS, id))
            if (!article && main != null) fetch(id)
        }
    }

    // ---------------- 抓取 ----------------
    val fetching = mutableStateMapOf<Long, Boolean>()
    fun fetch(taskId: Long, url: String? = null) {
        if (fetching[taskId] == true) return
        fetching[taskId] = true
        viewModelScope.launch {
            try {
                flushSourceNow()
                val r = repo.fetch(taskId, url)
                if (r.ok) toast(r.message)
            } catch (e: Exception) {
                toast("抓取出错：${e.message}")
            } finally { fetching.remove(taskId) }
        }
    }

    fun observeTask(id: Long): Flow<TaskEntity?> = repo.tasks.observe(id)
    fun observeLinks(id: Long): Flow<List<LinkEntity>> = repo.links.observeForTask(id)

    // 处理页原文编辑（防抖保存）
    private var sourceJob: Job? = null
    private var pendingSource: Pair<Long, String>? = null
    fun onSourceChange(taskId: Long, text: String) {
        pendingSource = taskId to text
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch { delay(600); flushSourceNow() }
    }
    fun flushSourceNow() {
        val p = pendingSource ?: return
        pendingSource = null
        viewModelScope.launch(NonCancellable) { repo.updateSource(p.first, p.second) }
    }

    fun updateTaskFields(taskId: Long, f: (TaskEntity) -> TaskEntity) = viewModelScope.launch {
        repo.tasks.get(taskId)?.let { repo.updateTask(f(it)) }
    }

    fun setLinkCategory(l: LinkEntity, c: LinkCategory) = viewModelScope.launch {
        repo.updateLink(l.copy(category = c.name, userConfirmed = true, categoryByAi = false, sendToModel = true))
    }
    fun setLinkNote(l: LinkEntity, note: String) = viewModelScope.launch { repo.updateLink(l.copy(userNote = note)) }

    suspend fun sendSummary(taskId: Long): String {
        val t = repo.tasks.get(taskId) ?: return ""
        return PromptBuilder.sendSummary(repo.buildInput(t, repo.links.forTask(taskId))) + " → " + UrlTools.hostOf(st.baseUrl)
    }

    // ---------------- 生成 ----------------
    val generating = mutableStateMapOf<Long, Boolean>()
    private val genJobs = HashMap<Long, Job>()
    var lastGenError = mutableStateMapOf<Long, String>()

    fun generate(taskId: Long, keepEdits: Boolean, openResult: Boolean = true) {
        if (generating[taskId] == true) return
        flushSourceNow()
        if (editTaskId == taskId) {
            if (keepEdits) flushEditorNow() else { editorJob?.cancel(); editorDirty = false }
        }
        generating[taskId] = true
        lastGenError.remove(taskId)
        genJobs[taskId] = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { delay(50) }
                val r = repo.generate(taskId, keepEdits)
                if (r.ok) {
                    toast(r.message)
                    if (openResult && current.name == Route.PROCESS && current.taskId == taskId) push(Route(Route.RESULT, taskId))
                } else lastGenError[taskId] = r.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) {
                    repo.tasks.get(taskId)?.let { repo.updateTask(it.copy(genStatus = "ERROR", genError = "已取消生成")) }
                }
                throw e
            } catch (e: Exception) {
                lastGenError[taskId] = "生成出错：${e.message}"
            } finally {
                generating.remove(taskId); genJobs.remove(taskId)
            }
        }
    }
    fun cancelGenerate(taskId: Long) { genJobs[taskId]?.cancel() }

    // ---------------- 结果页编辑 ----------------
    var editTaskId by mutableLongStateOf(0L)
        private set
    var editTitle by mutableStateOf("")
        private set
    var editBody by mutableStateOf("")
        private set
    var editorDirty by mutableStateOf(false)
        private set
    var saveState by mutableStateOf("")
    private var loadedStamp = 0L
    private var editorJob: Job? = null

    /** 结果页观察到任务后同步编辑器：首次进入或新一次生成覆盖时载入，用户正在编辑时不覆盖。 */
    fun syncEditor(t: TaskEntity) {
        val stamp = t.generatedAt
        if (editTaskId != t.id) {
            flushEditorNow()
            editTaskId = t.id; editTitle = t.finalTitle; editBody = t.finalBody; editorDirty = false; loadedStamp = stamp
            saveState = if (t.manuallySaved) "已保存" else if (t.userEdited) "已自动保存草稿" else ""
        } else if (stamp != loadedStamp && !editorDirty) {
            editTitle = t.finalTitle; editBody = t.finalBody; loadedStamp = stamp
            saveState = ""
        }
    }
    fun onTitleChange(v: String) { editTitle = v; markDirty() }
    fun onBodyChange(v: String) { editBody = v; markDirty() }
    fun chooseTitle(v: String) { editTitle = v; markDirty() }
    fun replaceBody(v: String) { editBody = v; markDirty() }
    private fun markDirty() {
        editorDirty = true; saveState = "正在编辑…"
        editorJob?.cancel()
        editorJob = viewModelScope.launch { delay(800); flushEditorNow() }
    }
    fun flushEditorNow(manual: Boolean = false) {
        val id = editTaskId
        if (id == 0L || (!editorDirty && !manual)) return
        val title = editTitle; val body = editBody
        val wasDirty = editorDirty
        editorJob?.cancel()
        viewModelScope.launch(NonCancellable) {
            try {
                val t = repo.tasks.get(id) ?: return@launch
                repo.updateTask(t.copy(finalTitle = title, finalBody = body,
                    userEdited = t.userEdited || wasDirty, manuallySaved = manual || t.manuallySaved))
                editorDirty = false
                saveState = if (manual) "已保存" else "已自动保存草稿"
                if (manual) toast("已保存到历史记录")
            } catch (e: Exception) {
                saveState = "保存失败"
                toast("保存失败：${e.message}，内容仍保留在编辑器中")
            }
        }
    }

    fun composeFull(links: List<LinkEntity>) = Composer.composeFull(editTitle, editBody, linkViews(links))
    fun bodyExpanded(links: List<LinkEntity>) = Composer.expand(editBody, linkViews(links))
    fun linkViews(links: List<LinkEntity>) = links.map {
        Composer.LinkView(it.linkId, it.rawUrl, it.resolvedUrl, LinkCategory.fromName(it.category), it.name, it.sendToModel)
    }

    // ---------------- 历史 ----------------
    var historyQuery by mutableStateOf("")
    val history: StateFlow<List<TaskEntity>> = snapshotFlow { historyQuery.trim() }.debounce(200)
        .flatMapLatest { q -> if (q.isEmpty()) repo.tasks.observeAll() else repo.tasks.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val taskCount: StateFlow<Int> = repo.tasks.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deleteTask(id: Long) = viewModelScope.launch { repo.tasks.delete(id); if (editTaskId == id) { editTaskId = 0; editorDirty = false }; toast("已删除") }
    fun clearHistory() = viewModelScope.launch { repo.tasks.deleteAll(); editTaskId = 0; editorDirty = false; toast("历史记录已清空") }
    suspend fun linksOf(id: Long) = repo.links.forTask(id)

    // ---------------- 设置 ----------------
    var sBaseUrl by mutableStateOf(st.baseUrl)
    var sModel by mutableStateOf(st.model)
    var sJina by mutableStateOf(st.jinaEnabled)
    var sKeyMasked by mutableStateOf("")
    var sKeyAvailable by mutableStateOf(true)
    var sJinaKeySet by mutableStateOf(false)
    fun saveJinaKey(k: String) { repo.keys.jinaKey = k; refreshSettingsState(); toast(if (k.isBlank()) "已清除 Jina Key" else "Jina Key 已加密保存") }
    var sMaxChars by mutableIntStateOf(st.maxChars)
    var sPrompt by mutableStateOf(st.prompt)
    var sPromptVersion by mutableIntStateOf(st.promptVersion)
    var sPromptUpdated by mutableLongStateOf(st.promptUpdatedAt)
    var connTesting by mutableStateOf(false)
    var connResult by mutableStateOf<Pair<Boolean, String>?>(null)
    val snapshots = repo.prompts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshSettingsState() {
        sBaseUrl = st.baseUrl; sModel = st.model; sJina = st.jinaEnabled; sMaxChars = st.maxChars
        sKeyMasked = repo.keys.masked(); sJinaKeySet = repo.keys.jinaKey.isNotBlank(); sKeyAvailable = repo.keys.available
        sPrompt = st.prompt; sPromptVersion = st.promptVersion; sPromptUpdated = st.promptUpdatedAt
    }
    fun saveApiKey(k: String) {
        if (!repo.keys.available) { toast("本机加密存储不可用，无法安全保存 API Key"); return }
        repo.keys.apiKey = k; refreshSettingsState(); connResult = null; toast(if (k.isBlank()) "已清除 API Key" else "API Key 已加密保存")
    }
    fun saveBaseUrl(v: String) {
        val u = v.trim().ifBlank { DeepSeekClient.DEFAULT_BASE_URL }
        if (!UrlTools.isHttpUrl(u)) { toast("Base URL 需以 http:// 或 https:// 开头"); return }
        st.baseUrl = u; refreshSettingsState(); connResult = null
    }
    fun saveModel(v: String) { st.model = v.trim().ifBlank { DeepSeekClient.DEFAULT_MODEL }; refreshSettingsState(); connResult = null }
    fun setJina(v: Boolean) { st.jinaEnabled = v; refreshSettingsState() }
    fun setMaxChars(v: Int) { st.maxChars = v; refreshSettingsState() }
    fun setDefaults(style: String?, length: String?) { style?.let { st.defaultStyle = it }; length?.let { st.defaultLength = it } }

    fun testConnection() {
        connTesting = true; connResult = null
        viewModelScope.launch {
            val r = repo.testConnection(st.baseUrl, st.model, repo.keys.apiKey)
            connResult = when (r) {
                is DeepSeekClient.Result.Success -> true to "${r.content} · 延迟 ${"%.1f".format(r.elapsedMs / 1000.0)}s"
                is DeepSeekClient.Result.Failure -> false to r.message
            }
            connTesting = false
        }
    }

    // ---------------- 提示词编辑器 ----------------
    var promptDraft by mutableStateOf(ssh.get<String>("promptDraft") ?: "")
        private set
    fun setDraft(v: String) { promptDraft = v; ssh["promptDraft"] = v }
    fun openPromptEditor() { setDraft(st.prompt); promptTestResult = null; push(Route(Route.PROMPT)) }
    var promptTesting by mutableStateOf(false)
    var promptTestResult by mutableStateOf<String?>(null)
    var promptTestTaskId by mutableLongStateOf(0L)

    fun savePromptDraft() = viewModelScope.launch {
        if (promptDraft.isBlank()) { toast("提示词不能为空"); return@launch }
        if (promptDraft == st.prompt) { toast("提示词没有变化"); return@launch }
        repo.savePrompt(promptDraft); refreshSettingsState(); toast("已保存为 v${st.promptVersion}，之后的生成都会使用新提示词")
    }
    fun restoreDefaultPrompt() = viewModelScope.launch {
        repo.restoreDefaultPrompt(); refreshSettingsState(); setDraft(st.prompt); toast("已恢复默认提示词（v${st.promptVersion}），历史记录和 API 配置未受影响")
    }
    fun restoreSnapshot(content: String) = viewModelScope.launch {
        repo.savePrompt(content, "从快照恢复"); refreshSettingsState(); setDraft(st.prompt); toast("已恢复为 v${st.promptVersion}")
    }
    /** 测试的是编辑器中当前（可能未保存）的文本。 */
    fun testPromptDraft() {
        if (promptDraft.isBlank()) { toast("提示词为空"); return }
        promptTesting = true; promptTestResult = null
        val text = promptDraft
        viewModelScope.launch {
            val (r, parsed) = repo.testPrompt(text, promptTestTaskId.takeIf { it > 0 })
            promptTestResult = when (r) {
                is DeepSeekClient.Result.Failure -> "❌ ${r.message}"
                is DeepSeekClient.Result.Success -> buildString {
                    append("✅ 使用编辑器中的提示词（${if (text == st.prompt) "与已保存版本相同" else "未保存版本"}）调用 ${r.model} 成功\n")
                    append(if (parsed?.structured == true) "结构：标准 JSON（标题 ${parsed.titles.size} 个，正文 ${parsed.body.length} 字）\n" else "结构：非标准输出（${parsed?.problem ?: ""}）\n")
                    append("\n—— 原始输出 ——\n").append(r.content)
                }
            }
            promptTesting = false
        }
    }

    // ---------------- 备份 ----------------
    var importPreview by mutableStateOf<Repository.ImportPreview?>(null)
    fun exportTo(uri: Uri) = viewModelScope.launch {
        try {
            val json = repo.exportJson(BuildConfig.VERSION_NAME)
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    ?: error("无法写入文件")
            }
            toast("备份已导出（不含 API Key）")
        } catch (e: Exception) { toast("导出失败：${e.message}") }
    }
    fun previewImport(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { s ->
                    val bytes = s.readBytes(); if (bytes.size > 50 * 1024 * 1024) error("文件过大"); String(bytes)
                } ?: error("无法读取文件")
            }
            importPreview = repo.previewImport(text)
        } catch (e: Exception) { toast("导入失败：${e.message}。现有数据未受影响") }
    }
    fun applyImport(prompt: Boolean, settings: Boolean) = viewModelScope.launch {
        val p = importPreview ?: return@launch
        importPreview = null
        try {
            val n = repo.applyImport(p, prompt, settings)
            refreshSettingsState()
            toast("导入完成：新增 $n 条记录，跳过重复 ${p.dupCount} 条")
        } catch (e: Exception) { toast("导入失败并已回滚：${e.message}") }
    }
}
