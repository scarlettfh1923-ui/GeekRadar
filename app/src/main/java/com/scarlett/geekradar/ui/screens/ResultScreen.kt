package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.data.TaskEntity
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.BarIconButton
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.GlassBottomBar
import com.scarlett.geekradar.ui.GroupCard
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.NoticeCard
import com.scarlett.geekradar.ui.PrimaryButton
import com.scarlett.geekradar.ui.Route
import com.scarlett.geekradar.ui.RowDivider
import com.scarlett.geekradar.ui.SectionLabel
import com.scarlett.geekradar.ui.SmallTitleBar
import com.scarlett.geekradar.ui.copyText
import com.scarlett.geekradar.ui.fmtTime
import com.scarlett.geekradar.ui.openUrl
import com.scarlett.geekradar.ui.theme.G
import org.json.JSONArray

fun jsonList(s: String): List<String> = runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

@Composable
fun ResultScreen(vm: AppViewModel, taskId: Long) {
    val ctx = LocalContext.current
    val task by vm.observeTask(taskId).collectAsState(initial = null)
    val links by vm.observeLinks(taskId).collectAsState(initial = emptyList())
    val t = task
    var regenAsk by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var noteEdit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(t) { t?.let { vm.syncEditor(it) } }
    DisposableEffect(taskId) { onDispose { vm.flushEditorNow() } }
    val generating = vm.generating[taskId] == true

    Column(Modifier.fillMaxSize()) {
        SmallTitleBar("编辑文案", subtitle = if (generating) "正在重新生成…" else vm.saveState.ifBlank { t?.let { "生成于 " + fmtTime(it.generatedAt) } },
            onBack = { vm.flushEditorNow(); vm.pop() }) {
            Box {
                Icon(Icons.Filled.MoreVert, "更多", tint = G.c.accent, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(6.dp).size(22.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("复制全部链接") }, onClick = { menu = false; copyText(ctx, "全部链接", com.scarlett.geekradar.core.Composer.allLinksText(vm.linkViews(links))) })
                    DropdownMenuItem(text = { Text("查看原文与链接（处理页）") }, onClick = { menu = false; vm.push(Route(Route.PROCESS, taskId)) })
                    DropdownMenuItem(text = { Text("查看模型原始输出") }, onClick = { menu = false; showRaw = true })
                    DropdownMenuItem(text = { Text("编辑备注") }, onClick = { menu = false; noteEdit = t?.note ?: "" })
                }
            }
        }
        if (t == null) { Text("记录不存在或已删除", Modifier.padding(24.dp), color = G.c.secondary); return@Column }
        val titles = jsonList(t.titleCandidatesJson)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            if (titles.isNotEmpty()) {
                SectionLabel("标题候选", "点选后填入下方标题，可再编辑")
                GroupCard {
                    titles.forEachIndexed { i, s ->
                        val sel = s == vm.editTitle
                        Row(Modifier.fillMaxWidth().clickable { vm.chooseTitle(s) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (sel) G.c.accent else G.c.fieldBg), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontSize = 11.sp, color = if (sel) Color.White else G.c.tertiary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(s, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = G.c.text, modifier = Modifier.weight(1f))
                            if (sel) Icon(Icons.Filled.Check, null, tint = G.c.accent, modifier = Modifier.size(16.dp))
                        }
                        if (i < titles.size - 1) RowDivider()
                    }
                }
            }
            SectionLabel("标题", "${vm.editTitle.length} 字")
            GroupCard(padding = true) { Field(vm.editTitle, vm::onTitleChange, "输入标题", minHeight = 24, fontSize = 16) }

            SectionLabel("正文", "${vm.editBody.length} 字 · [L2] 等编号复制时自动替换为原始链接")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.c.card).padding(14.dp)) {
                Field(vm.editBody, vm::onBodyChange, "正文为空，可手动编写或重新生成", minHeight = 240, fontSize = 14)
            }

            if (t.pendingBody.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                NoticeCard("新生成的版本（你选择了保留自己的修改）", listOf(t.pendingBody.take(160) + if (t.pendingBody.length > 160) "…" else ""), color = G.c.accent, bg = G.c.accentSoft) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniButton("用新版本替换正文") { vm.replaceBody(t.pendingBody); vm.updateTaskFields(taskId) { it.copy(pendingBody = "") } }
                        MiniButton("忽略") { vm.updateTaskFields(taskId) { it.copy(pendingBody = "") } }
                    }
                }
            }

            val warnings = jsonList(t.warningsJson)
            val facts = jsonList(t.factChecksJson)
            val unsure = jsonList(t.uncertainJson)
            if (t.genStatus == "ERROR" || t.genStatus == "INTERRUPTED") {
                Spacer(Modifier.height(10.dp)); NoticeCard("上次生成未完成", listOf(t.genError, "当前文案与链接均未改动"), color = G.c.danger)
            }
            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                NoticeCard("⚠ 需要注意", warnings, color = G.c.danger) {
                    if (!t.structuredOk) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniButton("查看原始输出") { showRaw = true }
                        MiniButton("重新生成") { if (t.userEdited || vm.editorDirty) regenAsk = true else vm.generate(taskId, false) }
                    }
                }
            }
            if (facts.isNotEmpty()) { Spacer(Modifier.height(10.dp)); NoticeCard("事实核查提醒", facts) }
            if (unsure.isNotEmpty()) { Spacer(Modifier.height(10.dp)); NoticeCard("不确定信息", unsure) }

            SectionLabel("资源链接 · ${links.count { it.category == LinkCategory.RESOURCE.name || it.category == LinkCategory.SOURCE.name }}", "程序独立保存 · 不经 AI 改写") {
                MiniButton("复制全部") { copyText(ctx, "全部链接", com.scarlett.geekradar.core.Composer.allLinksText(vm.linkViews(links))) }
            }
            LinkList(links, onCategory = { l, c -> vm.setLinkCategory(l, c) }, primaryOnly = true)
            Text("模型：${t.modelName.ifBlank { "—" }} · 提示词 ${if (t.promptVersion < 0) "测试版" else "v${t.promptVersion}"} · 生成于 ${fmtTime(t.generatedAt)}" +
                if (t.note.isNotBlank()) "\n备注：${t.note}" else "",
                fontSize = 11.sp, color = G.c.tertiary, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 20.dp))
        }
        GlassBottomBar {
            PrimaryButton("复制完整文案", Modifier.weight(1f)) { copyText(ctx, "完整文案", vm.composeFull(links)) }
            BarIconButton(Icons.Filled.Create, "仅正文") { copyText(ctx, "正文", vm.bodyExpanded(links)) }
            BarIconButton(Icons.Filled.Refresh, if (generating) "生成中" else "重新生成", enabled = !generating) {
                if (t.userEdited || vm.editorDirty) regenAsk = true else vm.generate(taskId, keepEdits = false)
            }
            BarIconButton(Icons.Filled.Check, "保存") { vm.flushEditorNow(manual = true) }
        }
    }

    if (regenAsk) RegenDialog(onDismiss = { regenAsk = false },
        onOverwrite = { vm.generate(taskId, keepEdits = false) }, onKeep = { vm.generate(taskId, keepEdits = true) })
    if (showRaw && t != null) AlertDialog(
        onDismissRequest = { showRaw = false }, containerColor = G.c.card,
        title = { Text("模型原始输出") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(t.rawModelOutput.ifBlank { "（无）" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        },
        confirmButton = { TextButton(onClick = { showRaw = false }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { copyText(ctx, "原始输出", t.rawModelOutput) }) { Text("复制") } },
    )
    noteEdit?.let { n ->
        AlertDialog(
            onDismissRequest = { noteEdit = null }, containerColor = G.c.card, title = { Text("备注") },
            text = { GroupCard(padding = true) { Field(n, { noteEdit = it }, "给这条记录写点备注", minHeight = 80) } },
            confirmButton = { TextButton(onClick = { vm.updateTaskFields(taskId) { it.copy(note = n) }; noteEdit = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { noteEdit = null }) { Text("取消") } },
        )
    }
}

/** 已手动修改过文案时，重新生成前询问。 */
@Composable
fun RegenDialog(onDismiss: () -> Unit, onOverwrite: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = G.c.card,
        title = { Text("你已修改过文案") },
        text = { Text("重新生成会产生新的标题和正文。要覆盖你的修改，还是保留修改、把新版本放在一旁参考？") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onDismiss(); onKeep() }) { Text("保留我的修改") }
                TextButton(onClick = { onDismiss(); onOverwrite() }) { Text("覆盖修改", color = G.c.danger) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
