package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.PromptBuilder
import com.scarlett.geekradar.data.Repository
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.Chip
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.GlassBottomBar
import com.scarlett.geekradar.ui.GroupCard
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.NoticeCard
import com.scarlett.geekradar.ui.PrimaryButton
import com.scarlett.geekradar.ui.Route
import com.scarlett.geekradar.ui.SectionLabel
import com.scarlett.geekradar.ui.Segmented
import com.scarlett.geekradar.ui.SmallTitleBar
import com.scarlett.geekradar.ui.openUrl
import com.scarlett.geekradar.ui.readClipboard
import com.scarlett.geekradar.ui.theme.G

@Composable
fun ProcessScreen(vm: AppViewModel, taskId: Long) {
    val ctx = LocalContext.current
    val task by vm.observeTask(taskId).collectAsState(initial = null)
    val links by vm.observeLinks(taskId).collectAsState(initial = emptyList())
    val t = task
    val fetching = vm.fetching[taskId] == true
    val generating = vm.generating[taskId] == true
    var source by rememberSaveable(taskId) { mutableStateOf<String?>(null) }
    var titleText by rememberSaveable(taskId) { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("") }
    var regenAsk by remember { mutableStateOf(false) }
    if (regenAsk) RegenDialog(onDismiss = { regenAsk = false }, onOverwrite = { vm.generate(taskId, false) }, onKeep = { vm.generate(taskId, true) })
    // 数据库中的原文变化（如抓取完成）且用户没有在编辑时，同步到输入框
    LaunchedEffect(t?.sourceText, t?.fetchedAt) { if (t != null && (source == null || !t.sourceEdited)) source = t.sourceText }
    LaunchedEffect(t?.pageTitle) { if (t != null) titleText = t.pageTitle }
    LaunchedEffect(t?.updatedAt, links.size) { summary = vm.sendSummary(taskId) }
    DisposableEffect(taskId) { onDispose { vm.flushSourceNow() } }

    Column(Modifier.fillMaxSize()) {
        SmallTitleBar("解析与准备", subtitle = t?.let { statusLabel(it.fetchStatus, it.fetchChannel, fetching) }, onBack = { vm.flushSourceNow(); vm.pop() })
        if (t == null) { Text("记录不存在或已删除", Modifier.padding(24.dp), color = G.c.secondary); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            // ---- 抓取状态 ----
            when {
                fetching -> GroupCard(padding = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = G.c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("正在获取网页内容…" + if (vm.sJina) "（本机失败会自动尝试备用读取通道）" else "", fontSize = 13.5.sp, color = G.c.text)
                    }
                }
                t.fetchStatus == "FAILED" || (t.fetchStatus == "PARTIAL" && !t.sourceEdited) -> NoticeCard(
                    if (t.fetchStatus == "FAILED") Repository.FETCH_FAIL else "只获取到部分内容，建议补充粘贴原文。",
                    listOfNotNull(t.fetchError.takeIf { it.isNotBlank() }, t.originalUrl?.let { "原始链接已保留：$it" }),
                ) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniButton("粘贴原文") { val c = readClipboard(ctx); if (c.isBlank()) vm.toast("剪贴板为空，请先在浏览器中复制原文") else { source = c; vm.onSourceChange(taskId, c) } }
                        MiniButton("重试抓取") { vm.fetch(taskId) }
                        t.originalUrl?.let { u -> MiniButton("在浏览器打开") { openUrl(ctx, u) } }
                    }
                }
                t.fetchStatus == "OK" || t.fetchStatus == "PARTIAL" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (t.fetchChannel == "jina") "✓ 已通过备用通道 Jina Reader 获取" else "✓ 已获取网页内容")
                    Spacer(Modifier.weight(1f))
                    MiniButton("重新抓取") { vm.fetch(taskId) }
                }
                t.fetchStatus == "MANUAL" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip("📝 使用粘贴的原文")
                    Spacer(Modifier.weight(1f))
                    t.originalUrl?.let { MiniButton("尝试抓取链接") { vm.fetch(taskId) } }
                    if (t.originalUrl == null) links.firstOrNull()?.let { l -> MiniButton("抓取 ${l.linkId}") { vm.fetch(taskId, l.best()) } }
                }
                else -> t.originalUrl?.let { MiniButton("抓取网页") { vm.fetch(taskId) } }
            }

            if (t.rawInput.isNotBlank() && t.inputType != "text") {
                SectionLabel("原始输入", "完整保留，不会被修改")
                GroupCard(padding = true) { SelectionContainer { Text(t.rawInput, fontSize = 13.sp, color = G.c.text.copy(alpha = 0.85f)) } }
            }

            SectionLabel("网页标题")
            GroupCard(padding = true) {
                Field(titleText ?: "", { titleText = it; vm.updateTaskFields(taskId) { x -> x.copy(pageTitle = it) } }, "未获取到标题，可手动填写", singleLine = false, minHeight = 24)
            }

            SectionLabel("原文", "${(source ?: "").length} 字 · 可编辑，抓取失败时直接粘贴") {
                MiniButton("粘贴") { val c = readClipboard(ctx); if (c.isNotBlank()) { source = c; vm.onSourceChange(taskId, c) } else vm.toast("剪贴板为空") }
            }
            GroupCard(padding = true) {
                Field(source ?: "", { source = it; vm.onSourceChange(taskId, it) }, "在这里粘贴网页原文或文章内容…", minHeight = 160, fontSize = 14)
            }

            SectionLabel("链接清单 · ${links.size}", "程序独立提取保存，点类别可修改")
            LinkList(links, onCategory = { l, c -> vm.setLinkCategory(l, c) })

            SectionLabel("风格与长度")
            GroupCard(padding = true) {
                Segmented(PromptBuilder.STYLES, t.style) { s -> vm.updateTaskFields(taskId) { it.copy(style = s) } }
                Spacer(Modifier.height(8.dp))
                Segmented(PromptBuilder.LENGTHS, t.length) { s -> vm.updateTaskFields(taskId) { it.copy(length = s) } }
            }
            Text("🔒 $summary。仅在点击生成时发送，历史记录不会上传。", fontSize = 11.5.sp, color = G.c.tertiary, modifier = Modifier.padding(start = 6.dp, top = 12.dp))

            (vm.lastGenError[taskId] ?: t.genError.takeIf { t.genStatus == "ERROR" || t.genStatus == "INTERRUPTED" })?.let { err ->
                Spacer(Modifier.height(10.dp))
                NoticeCard("生成未完成", listOf(err, "原文、链接和已编辑内容都已保留，可修改设置后重试。"), color = G.c.danger) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniButton("重试") { vm.generate(taskId, keepEdits = true) }
                        MiniButton("去设置") { vm.push(Route(Route.SETTINGS)) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        GlassBottomBar {
            if (t.genStatus == "DONE" || t.finalBody.isNotBlank()) {
                MiniButton("查看结果") { vm.push(Route(Route.RESULT, taskId)) }
            }
            if (generating) {
                PrimaryButton("正在生成…", Modifier.weight(1f), loading = true) {}
                MiniButton("取消") { vm.cancelGenerate(taskId) }
            } else {
                PrimaryButton(if (t.genStatus == "DONE") "重新生成文案" else "生成文案", Modifier.weight(1f), enabled = !fetching) {
                    if (t.userEdited) regenAsk = true else vm.generate(taskId, keepEdits = false)
                }
            }
        }
    }
}

fun statusLabel(s: String, ch: String, fetching: Boolean) = when {
    fetching -> "正在抓取"
    s == "OK" -> if (ch == "jina") "已获取（备用通道）" else "已获取"
    s == "PARTIAL" -> "部分内容"
    s == "FAILED" -> "抓取失败"
    s == "MANUAL" -> "粘贴原文"
    else -> "待抓取"
}
