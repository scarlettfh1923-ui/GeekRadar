package com.scarlett.geekradar.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.Composer
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.core.UrlTools
import com.scarlett.geekradar.data.LinkEntity
import com.scarlett.geekradar.data.SettingsStore
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.BarIconButton
import com.scarlett.geekradar.ui.CategoryTag
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
import com.scarlett.geekradar.ui.share.XhsShare
import com.scarlett.geekradar.ui.theme.G
import org.json.JSONArray

fun jsonList(s: String): List<String> = runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

/** 结果页主列表中展示的链接：主来源链接、资源链接、发给模型的待确认链接；其余（辅助、页面导航、页面里的其他作者链接）折叠。 */
fun primaryLinks(links: List<LinkEntity>): List<LinkEntity> = links.filter {
    (it.category == LinkCategory.SOURCE.name && it.sourceLocation == "input") || it.category == LinkCategory.RESOURCE.name ||
        (it.category == LinkCategory.PENDING.name && it.sendToModel)
}.sortedBy { (when (it.category) { "SOURCE" -> 0; "RESOURCE" -> 1; else -> 2 }) * 10000 + it.position }

@OptIn(ExperimentalLayoutApi::class)
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
    var addTag by remember { mutableStateOf<String?>(null) }
    var linksOpen by rememberSaveable { mutableStateOf(false) }
    var factsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(t) { t?.let { vm.syncEditor(it) } }
    DisposableEffect(taskId) { onDispose { vm.flushEditorNow() } }
    // 跳转小红书由界面执行（需要 Activity 上下文）
    LaunchedEffect(Unit) {
        vm.launchRequests.collect { s ->
            val f = s.coverFile
            val r = if (s.coverMode == SettingsStore.COVER_GENERATED && f != null) XhsShare.shareCover(ctx, XhsShare.uriFor(ctx, f)) else XhsShare.openApp(ctx)
            vm.onLaunchResult(r)
        }
    }
    val generating = vm.generating[taskId] == true

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val savedHint = vm.saveState.ifBlank { if (t?.manuallySaved == true) "已保存" else "" }
        SmallTitleBar("编辑文案",
            subtitle = if (generating) "正在重新生成…" else t?.let { "生成于 " + fmtTime(it.generatedAt) + if (savedHint.isNotBlank()) " · $savedHint" else "" },
            onBack = { vm.flushEditorNow(); vm.pop() }) {
            Box {
                Icon(Icons.Filled.MoreVert, "更多", tint = G.c.accent, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(6.dp).size(22.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("复制完整文案") }, onClick = { menu = false; copyText(ctx, "完整文案", vm.composeFull(links)) })
                    DropdownMenuItem(text = { Text("仅复制正文") }, onClick = { menu = false; copyText(ctx, "正文", vm.bodyExpanded(links)) })
                    DropdownMenuItem(text = { Text("复制全部链接") }, onClick = { menu = false; copyText(ctx, "全部链接", Composer.allLinksText(vm.linkViews(links))) })
                    DropdownMenuItem(text = { Text("查看原文与链接（处理页）") }, onClick = { menu = false; vm.push(Route(Route.PROCESS, taskId)) })
                    DropdownMenuItem(text = { Text("查看模型原始输出") }, onClick = { menu = false; showRaw = true })
                    DropdownMenuItem(text = { Text("编辑备注") }, onClick = { menu = false; noteEdit = t?.note ?: "" })
                }
            }
        }
        if (t == null) { Text("记录不存在或已删除", Modifier.padding(24.dp), color = G.c.secondary); return@Column }
        val titles = jsonList(t.titleCandidatesJson)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            // ---------- 标题 ----------
            SectionLabel("标题") { Counter("${vm.editTitle.length} / 20", vm.editTitle.length > 20) }
            GroupCard {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Field(vm.editTitle, vm::onTitleChange, "输入标题", minHeight = 24, fontSize = 17, bold = true)
                }
                if (titles.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
                    Text("AI 候选 · 点选替换", fontSize = 11.sp, color = G.c.tertiary, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 16.dp, top = 9.dp, bottom = 2.dp))
                    titles.forEachIndexed { i, s ->
                        val sel = s == vm.editTitle
                        Row(Modifier.fillMaxWidth().clickable { vm.chooseTitle(s) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(19.dp).clip(CircleShape).background(if (sel) G.c.accent else G.c.fieldBg), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontSize = 10.5.sp, color = if (sel) Color.White else G.c.tertiary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(s, fontSize = 13.5.sp, color = if (sel) G.c.text else G.c.secondary, modifier = Modifier.weight(1f))
                            if (sel) Icon(Icons.Filled.Check, null, tint = G.c.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ---------- 正文 ----------
            SectionLabel("正文", "[L2] 等编号分享时自动换成原始链接") { Counter("${vm.editBody.length} 字", false) }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.c.card).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Field(vm.editBody, vm::onBodyChange, "正文为空，可手动编写或重新生成", minHeight = 200, fontSize = 14)
            }

            // ---------- 标签 ----------
            SectionLabel("标签") { Counter("${vm.editTags.size} 个 · 分享时单独写入", false) }
            GroupCard {
                FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.editTags.forEach { tag -> TagChip(tag) { vm.removeTag(tag) } }
                    AddChip { addTag = "" }
                }
                val trailing = vm.trailingTagsInBody()
                if (trailing.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("正文末尾有 ${trailing.size} 个话题标签", fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.weight(1f))
                        MiniButton("移到标签区") { vm.moveTrailingTagsToTags() }
                    }
                }
            }

            // ---------- 提醒 ----------
            val sourceWarn = vm.sourceWarnings(links)
            if (sourceWarn.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                NoticeCard("文案里可能还有原帖 / 原作者信息", sourceWarn, color = G.c.warn, bg = G.c.warnSoft) {
                    Text("这只是提醒，程序不会改动你的文字。", fontSize = 11.5.sp, color = G.c.tertiary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (t.pendingBody.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp)); NoticeCard("上次生成未完成", listOf(t.genError, "当前文案与链接均未改动"), color = G.c.danger)
            }
            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                NoticeCard("⚠ 需要注意", warnings, color = G.c.danger) {
                    if (!t.structuredOk) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniButton("查看原始输出") { showRaw = true }
                        MiniButton("重新生成") { if (t.userEdited || vm.editorDirty) regenAsk = true else vm.generate(taskId, false) }
                    }
                }
            }

            // ---------- 资源链接（默认折叠） ----------
            Spacer(Modifier.height(16.dp))
            val prim = primaryLinks(links)
            val others = links.filter { it !in prim }
            val pendingN = prim.count { it.category == LinkCategory.PENDING.name }
            GroupCard {
                FoldHeader(Icons.Filled.Share, "资源链接", "${prim.size} 条" + if (pendingN > 0) " · $pendingN 条待确认" else "", linksOpen) { linksOpen = !linksOpen }
                AnimatedVisibility(linksOpen) {
                    Column {
                        if (prim.isEmpty()) Text("暂无资源链接", fontSize = 13.sp, color = G.c.tertiary, modifier = Modifier.padding(16.dp))
                        prim.forEach { l ->
                            HorizontalDivider(thickness = 0.5.dp, color = G.c.divider, modifier = Modifier.padding(horizontal = 14.dp))
                            CompactLink(l) { c -> vm.setLinkCategory(l, c) }
                        }
                        if (others.isNotEmpty()) {
                            var showOthers by rememberSaveable { mutableStateOf(false) }
                            HorizontalDivider(thickness = 0.5.dp, color = G.c.divider, modifier = Modifier.padding(horizontal = 14.dp))
                            Text((if (showOthers) "收起" else "展开") + "其他链接 ${others.size} 条（辅助、页面导航、页面中的其他账号等，仅保存）",
                                fontSize = 12.sp, color = G.c.accent, modifier = Modifier.fillMaxWidth().clickable { showOthers = !showOthers }.padding(horizontal = 16.dp, vertical = 11.dp))
                            if (showOthers) others.sortedBy { it.position }.forEach { l ->
                                HorizontalDivider(thickness = 0.5.dp, color = G.c.divider, modifier = Modifier.padding(horizontal = 14.dp))
                                CompactLink(l) { c -> vm.setLinkCategory(l, c) }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("复制全部链接", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = G.c.accent,
                                modifier = Modifier.clickable { copyText(ctx, "全部链接", Composer.allLinksText(vm.linkViews(links))) })
                            Spacer(Modifier.weight(1f))
                            Text("原样保存，不经 AI 改写 · 来源不进文案", fontSize = 11.sp, color = G.c.tertiary)
                        }
                    }
                }
            }

            // ---------- 事实核查（默认折叠） ----------
            Spacer(Modifier.height(12.dp))
            GroupCard {
                val n = facts.size + unsure.size
                FoldHeader(Icons.Filled.Info, "事实核查", if (n == 0) "暂无提醒" else "$n 条提醒", factsOpen, iconBg = G.c.warnSoft, iconTint = G.c.warn) { factsOpen = !factsOpen }
                AnimatedVisibility(factsOpen) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                        if (n == 0) Text("模型没有给出需要核对的事实。发布前仍建议自己核对数字、价格和时效信息。", fontSize = 12.5.sp, color = G.c.secondary)
                        if (facts.isNotEmpty()) { Text("发布前核对", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = G.c.warn, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)); facts.forEach { Bullet(it) } }
                        if (unsure.isNotEmpty()) { Text("不确定信息", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = G.c.warn, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)); unsure.forEach { Bullet(it) } }
                    }
                }
            }

            Text("模型：${t.modelName.ifBlank { "—" }} · 提示词 ${if (t.promptVersion < 0) "测试版" else "v${t.promptVersion}"} · 生成于 ${fmtTime(t.generatedAt)}" +
                if (t.note.isNotBlank()) "\n备注：${t.note}" else "",
                fontSize = 11.sp, color = G.c.tertiary, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 20.dp))
        }
        GlassBottomBar {
            PrimaryButton("分享到小红书", Modifier.weight(1f), icon = Icons.Filled.Share) { vm.shareToXhs(links) }
            BarIconButton(Icons.Filled.Refresh, if (generating) "生成中" else "重新生成", enabled = !generating) {
                if (t.userEdited || vm.editorDirty) regenAsk = true else vm.generate(taskId, keepEdits = false)
            }
            BarIconButton(Icons.Filled.Check, "保存") { vm.flushEditorNow(manual = true) }
        }
    }
    vm.shareSheet?.let { ShareSheetOverlay(vm, it) }
    }

    addTag?.let { v ->
        AlertDialog(
            onDismissRequest = { addTag = null }, containerColor = G.c.card, title = { Text("添加标签") },
            text = {
                Column {
                    GroupCard(padding = true, modifier = Modifier.background(G.c.fieldBg)) { Field(v, { addTag = it }, "例如：AI工具 效率神器", singleLine = true, minHeight = 24, fontSize = 15) }
                    Text("可一次输入多个，用空格或逗号分开，# 号可写可不写。", fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.addTags(v); addTag = null }) { Text("添加") } },
            dismissButton = { TextButton(onClick = { addTag = null }) { Text("取消") } },
        )
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

@Composable
private fun Counter(text: String, over: Boolean) = Text(text, fontSize = 11.5.sp, color = if (over) G.c.warn else G.c.tertiary)

@Composable
private fun Bullet(s: String) = Text("· $s", fontSize = 12.5.sp, lineHeight = 19.sp, color = G.c.text.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp))

@Composable
fun TagChip(tag: String, onRemove: () -> Unit) {
    Row(Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(G.c.accentSoft).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#$tag", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = G.c.accent, maxLines = 1)
        Box(Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "删除标签 $tag", tint = G.c.accent.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
fun AddChip(onClick: () -> Unit) {
    Row(Modifier.height(30.dp).clip(RoundedCornerShape(50)).border(1.dp, G.c.accent.copy(alpha = 0.3f), RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+ 添加", fontSize = 13.sp, color = G.c.secondary)
    }
}

/** 可折叠分组的标题行：图标 + 标题 + 摘要 + 展开箭头。 */
@Composable
fun FoldHeader(icon: ImageVector, title: String, summary: String, open: Boolean, iconBg: Color = G.c.accentSoft, iconTint: Color = G.c.accent, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = G.c.text)
        Spacer(Modifier.width(8.dp))
        Text(summary, fontSize = 12.5.sp, color = G.c.tertiary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (open) "收起" else "展开", tint = G.c.tertiary, modifier = Modifier.size(20.dp))
    }
}

/** 结果页的紧凑链接卡：类别 · 名称 · 用途 · 原始 URL（等宽完整显示）· 复制 / 打开。 */
@Composable
fun CompactLink(l: LinkEntity, onCategory: (LinkCategory) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val cat = LinkCategory.fromName(l.category)
    val isSource = cat == LinkCategory.SOURCE
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                CategoryTag(cat, onClick = { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    LinkCategory.entries.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { menu = false; onCategory(c) }) }
                }
            }
            Spacer(Modifier.width(7.dp))
            val name = l.name.ifBlank { if (isSource && l.sourceLocation == "input") "分享来源" else l.anchorText.ifBlank { UrlTools.hostOf(l.best()) } }
            Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (isSource) G.c.secondary else G.c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SmallAction("复制") { copyText(ctx, "链接", l.best()) }
            Spacer(Modifier.width(6.dp))
            SmallAction("打开") { openUrl(ctx, l.best()) }
        }
        val sub = when {
            l.purpose.isNotBlank() -> l.purpose
            isSource && l.sourceLocation == "input" -> "你最初分享的页面"
            l.programNote.isNotBlank() -> l.programNote
            else -> ""
        }
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sub.isNotBlank()) Text(sub, fontSize = 11.5.sp, color = G.c.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            if (isSource) {
                Spacer(Modifier.width(6.dp))
                Text(LinkCategory.SOURCE_NOTE, fontSize = 10.5.sp, color = G.c.tertiary,
                    modifier = Modifier.border(1.dp, G.c.divider, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
            }
        }
        SelectionContainer(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(l.rawUrl, fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace, color = G.c.text.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(G.c.fieldBg).padding(horizontal = 10.dp, vertical = 7.dp))
        }
        if (l.resolvedUrl.isNotBlank() && l.resolvedUrl != l.rawUrl)
            Text("解析为：" + l.resolvedUrl, fontSize = 10.5.sp, color = G.c.tertiary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 11.5.sp, color = G.c.accent, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, G.c.divider, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 4.dp))
}

/** 「分享到小红书」后的进度弹层。 */
@Composable
fun ShareSheetOverlay(vm: AppViewModel, s: AppViewModel.ShareSheet) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (s.finished) vm.dismissShare() }) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(G.c.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(G.c.divider))
            Text("准备好发布了", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = G.c.text, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
            Text("小红书打开后，在标题、正文处长按粘贴（输入法剪贴板历史里有这 ${s.count} 条）", fontSize = 12.5.sp, color = G.c.secondary, modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 12.dp))
            GroupCard {
                StepRow(s.clip, null)
                RowDivider()
                val thumb = s.coverFile?.let { f -> remember(f) { runCatching { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }.getOrNull() } }
                StepRow(s.cover, thumb?.let { b -> { Image(b, "封面预览", contentScale = ContentScale.Crop, modifier = Modifier.size(width = 42.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).border(0.5.dp, G.c.divider, RoundedCornerShape(6.dp))) } })
                RowDivider()
                StepRow(s.open, null)
            }
            Row(Modifier.padding(top = 10.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("单独再复制：", fontSize = 12.sp, color = G.c.tertiary)
                if (s.title.isNotBlank()) MiniButton("标题") { vm.copyOne("标题", s.title) }
                if (s.body.isNotBlank()) MiniButton("正文") { vm.copyOne("正文", s.body) }
                if (s.tagLine.isNotBlank()) MiniButton("标签") { vm.copyOne("标签", s.tagLine) }
            }
            Row(Modifier.padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(G.c.chipNeutral).clickable { vm.dismissShare() }, contentAlignment = Alignment.Center) {
                    Text("留在这里", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.c.text)
                }
                PrimaryButton("去小红书粘贴", Modifier.weight(1.4f), enabled = s.clip.state != AppViewModel.StepState.RUNNING) { vm.relaunchXhs() }
            }
        }
    }
}

@Composable
private fun StepRow(st: AppViewModel.ShareStep, trailing: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (st.state) {
                AppViewModel.StepState.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = G.c.accent)
                AppViewModel.StepState.DONE -> Box(Modifier.size(20.dp).clip(CircleShape).background(G.c.accent), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                AppViewModel.StepState.FAILED -> Icon(Icons.Filled.Warning, null, tint = G.c.danger, modifier = Modifier.size(20.dp))
                AppViewModel.StepState.WAIT -> Box(Modifier.size(18.dp).clip(CircleShape).border(1.5.dp, G.c.divider, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(st.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (st.state == AppViewModel.StepState.WAIT) G.c.tertiary else G.c.text)
            Text(st.sub, fontSize = 11.5.sp, color = G.c.tertiary, modifier = Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
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
