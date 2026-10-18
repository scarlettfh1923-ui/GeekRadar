package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.Composer
import com.scarlett.geekradar.data.TaskEntity
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.ConfirmDialog
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.LargeTitleBar
import com.scarlett.geekradar.ui.Route
import com.scarlett.geekradar.ui.copyText
import com.scarlett.geekradar.ui.fmtDay
import com.scarlett.geekradar.ui.fmtTime
import com.scarlett.geekradar.ui.theme.G
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val list by vm.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TaskEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        LargeTitleBar("历史") {
            if (list.isNotEmpty() && vm.historyQuery.isBlank()) Text("清空", color = G.c.danger, fontSize = 15.sp, modifier = Modifier.clickable { confirmClear = true })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(G.c.card).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = G.c.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Field(vm.historyQuery, { vm.historyQuery = it }, "搜索标题、URL、正文关键词", singleLine = true, minHeight = 22, fontSize = 14)
        }
        if (list.isEmpty()) {
            Text(if (vm.historyQuery.isBlank()) "还没有历史记录" else "没有找到匹配的记录", color = G.c.tertiary, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            var lastDay = ""
            list.forEach { t ->
                val day = fmtDay(t.createdAt)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "d$day") { Text(day, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = G.c.secondary, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp)) }
                }
                item(key = t.id) { HistoryItem(vm, t) { confirmDelete = t } }
            }
            item { Spacer(Modifier.size(20.dp)) }
        }
    }
    if (confirmClear) ConfirmDialog("清空历史记录？", "将删除全部 ${list.size} 条记录及其链接清单，无法撤销。提示词和 API 配置不受影响。建议先在设置中导出备份。", "全部清空", danger = true,
        onDismiss = { confirmClear = false }) { vm.clearHistory() }
    confirmDelete?.let { t ->
        ConfirmDialog("删除这条记录？", "「${t.finalTitle.ifBlank { t.pageTitle.ifBlank { t.rawInput.take(30) } }}」及其链接清单将被删除，无法撤销。", "删除", danger = true,
            onDismiss = { confirmDelete = null }) { vm.deleteTask(t.id) }
    }
}

@Composable
private fun HistoryItem(vm: AppViewModel, t: TaskEntity, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val hasResult = t.genStatus == "DONE" || t.finalBody.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(G.c.card)
            .clickable { vm.push(Route(if (hasResult) Route.RESULT else Route.PROCESS, t.id)) }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.finalTitle.ifBlank { t.pageTitle.ifBlank { t.rawInput.take(40) } }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = G.c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.originalUrl ?: "粘贴原文", fontSize = 11.5.sp, color = G.c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (label, fg) = when {
                    t.manuallySaved -> "已保存" to G.c.accent
                    hasResult -> "草稿" to G.c.secondary
                    t.genStatus == "ERROR" || t.genStatus == "INTERRUPTED" -> "生成失败" to G.c.danger
                    else -> "未生成" to G.c.secondary
                }
                Text(label, fontSize = 11.sp, color = fg, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(G.c.fieldBg).padding(horizontal = 8.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(fmtTime(t.updatedAt) + (if (t.modelName.isNotBlank()) " · ${t.modelName}" else "") + (if (t.promptVersion > 0) " · 提示词 v${t.promptVersion}" else ""),
                    fontSize = 11.sp, color = G.c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            Icon(Icons.Filled.MoreVert, "更多", tint = G.c.tertiary, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(10.dp).size(20.dp))
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("继续编辑") }, onClick = { menu = false; vm.push(Route(if (hasResult) Route.RESULT else Route.PROCESS, t.id)) })
                DropdownMenuItem(text = { Text("重新生成（处理页）") }, onClick = { menu = false; vm.push(Route(Route.PROCESS, t.id)) })
                DropdownMenuItem(text = { Text("复制文案") }, onClick = {
                    menu = false
                    scope.launch { copyText(ctx, "完整文案", Composer.composeFull(t.finalTitle, t.finalBody, vm.linkViews(vm.linksOf(t.id)), com.scarlett.geekradar.core.Tags.fromJson(t.tagsJson))) }
                })
                DropdownMenuItem(text = { Text("复制链接") }, onClick = {
                    menu = false
                    scope.launch { copyText(ctx, "全部链接", Composer.allLinksText(vm.linkViews(vm.linksOf(t.id)))) }
                })
                DropdownMenuItem(text = { Text("删除", color = G.c.danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
