package com.scarlett.geekradar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.scarlett.geekradar.BuildConfig
import com.scarlett.geekradar.core.CoverLayout
import com.scarlett.geekradar.core.DefaultPrompt
import com.scarlett.geekradar.data.SettingsStore
import com.scarlett.geekradar.ui.share.CoverRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.scarlett.geekradar.core.DeepSeekClient
import com.scarlett.geekradar.core.UrlTools
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.Dot
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.GroupCard
import com.scarlett.geekradar.ui.LargeTitleBar
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.RowDivider
import com.scarlett.geekradar.ui.SectionLabel
import com.scarlett.geekradar.ui.SettingRow
import com.scarlett.geekradar.ui.fmtTime
import com.scarlett.geekradar.ui.theme.G

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<String?>(null) }   // key / model / base / chars
    var showSnapshots by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val count by vm.taskCount.collectAsState()
    val snaps by vm.snapshots.collectAsState()
    LaunchedEffect(Unit) { vm.refreshSettingsState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::previewImport) }

    Column(Modifier.fillMaxSize()) {
        LargeTitleBar("设置")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("分享到小红书")
            GroupCard {
                Text("封面方式", fontSize = 11.5.sp, color = G.c.tertiary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                CoverOption("使用小红书自带文字配图", "只写入文字并打开小红书，在发布页选「文字配图」", vm.sCoverMode == SettingsStore.COVER_XHS, null) { vm.setCoverMode(SettingsStore.COVER_XHS) }
                CoverOption("应用内生成封面图", "用标题生成 3:4 大字封面，存在应用缓存（不进相册），随分享发给小红书", vm.sCoverMode == SettingsStore.COVER_GENERATED, { CoverThumb() }) { vm.setCoverMode(SettingsStore.COVER_GENERATED) }
                RowDivider()
                SettingRow(Icons.Filled.DateRange, "剪贴板写入间隔", "标题 → 正文 → 标签 依次写入", "${fmtSec(vm.sClipInterval)} 秒") { editing = "interval" }
                RowDivider()
                SettingRow(Icons.Filled.Edit, "默认标签", "每篇生成后自动追加", vm.sDefaultTags.takeIf { it.isNotBlank() }?.split(" ")?.joinToString(" ") { "#$it" } ?: "未设置") { editing = "tags" }
                RowDivider()
                SettingRow(Icons.Filled.Send, "分享后自动打开小红书", "关闭后需在弹层中手动点击「去小红书粘贴」",
                    trailing = { Switch(vm.sAutoOpen, { vm.setAutoOpen(it) }, colors = SwitchDefaults.colors(checkedTrackColor = G.c.accent)) })
            }

            SectionLabel("模型与 API")
            GroupCard {
                SettingRow(Icons.Filled.Lock, "API Key", if (vm.sKeyAvailable) "已加密保存在本机（Android Keystore）" else "本机加密存储不可用", vm.sKeyMasked, mono = true) { editing = "key" }
                RowDivider()
                SettingRow(Icons.Filled.Star, "模型", "默认 ${DeepSeekClient.DEFAULT_MODEL}，可改为 deepseek-v4-pro 等", vm.sModel) { editing = "model" }
                RowDivider()
                SettingRow(Icons.Filled.Share, "Base URL", null, UrlTools.hostOf(vm.sBaseUrl).ifBlank { vm.sBaseUrl }, mono = true) { editing = "base" }
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        vm.connTesting -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        vm.connResult != null -> Dot(if (vm.connResult!!.first) G.c.ok else G.c.danger)
                        else -> Dot(G.c.tertiary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(vm.connResult?.second ?: if (vm.connTesting) "正在测试…" else "尚未测试连接", fontSize = 13.sp, color = G.c.text.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                    MiniButton("测试连接", enabled = !vm.connTesting) { vm.testConnection() }
                }
            }

            SectionLabel("网页读取")
            GroupCard {
                SettingRow(Icons.Filled.Search, "备用读取通道（Jina Reader）",
                    "本机抓取失败或正文过少时，自动通过 r.jina.ai 再读取一次。开启后该链接会经过第三方服务器（Jina AI），请勿用于私密链接。",
                    trailing = { Switch(vm.sJina, { vm.setJina(it) }, colors = SwitchDefaults.colors(checkedTrackColor = G.c.accent)) })
                RowDivider()
                SettingRow(Icons.Filled.Lock, "Jina API Key（可选）", "匿名调用被拒绝时填写，加密保存在本机", if (vm.sJinaKeySet) "已设置" else "未设置") { editing = "jina" }
                RowDivider()
                SettingRow(Icons.Filled.Build, "发送给模型的正文上限", "超过部分截断（链接清单不截断）", "${vm.sMaxChars} 字") { editing = "chars" }
            }

            SectionLabel("AI 提示词")
            GroupCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.sPrompt.trim() == DefaultPrompt.TEXT.trim()) DefaultPrompt.VERSION_LABEL else "自定义提示词", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = G.c.text, modifier = Modifier.weight(1f))
                        Text("使用中", fontSize = 11.sp, color = G.c.accent, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(G.c.accentSoft).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Text(vm.sPrompt.take(140) + "…", fontSize = 11.5.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace, color = G.c.text.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(G.c.fieldBg).padding(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("v${vm.sPromptVersion} · ${fmtTime(vm.sPromptUpdated)} 修改", fontSize = 12.sp, color = G.c.tertiary, modifier = Modifier.weight(1f))
                        Text("恢复默认", fontSize = 13.sp, color = G.c.secondary, modifier = Modifier.clickable { confirmRestore = true }.padding(6.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("编辑", fontSize = 13.sp, color = G.c.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.openPromptEditor() }.padding(6.dp))
                    }
                }
                RowDivider()
                SettingRow(Icons.AutoMirrored.Filled.List, "提示词版本快照", null, "${snaps.size} 个") { showSnapshots = true }
            }

            SectionLabel("数据备份")
            GroupCard {
                SettingRow(Icons.Filled.Edit, "导出 JSON 备份", "历史 $count 条 · 链接清单 · 提示词 · 设置（不含 API Key）") {
                    exportLauncher.launch("GeekRadar-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json")
                }
                RowDivider()
                SettingRow(Icons.Filled.Refresh, "从备份导入", "导入前会提示可能覆盖的内容，重复记录自动跳过") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }

            SectionLabel("关于")
            GroupCard {
                SettingRow(Icons.Filled.Info, "极客雷达 ${BuildConfig.VERSION_NAME}",
                    "历史记录、提示词和设置只保存在本机；仅在你点击生成时，把必要的正文和链接发送给所配置的模型接口。不会自动发布到小红书。")
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }

    editing?.let { which ->
        val (title, init, hint) = when (which) {
            "key" -> Triple("DeepSeek API Key", "", "sk-…（留空并保存可清除）")
            "jina" -> Triple("Jina API Key（可选）", "", "jina_…（留空并保存可清除）")
            "model" -> Triple("模型名称", vm.sModel, DeepSeekClient.DEFAULT_MODEL)
            "tags" -> Triple("默认标签", vm.sDefaultTags, "例如：AI工具 效率神器（空格分隔，留空表示不追加）")
            "interval" -> Triple("剪贴板写入间隔（秒）", fmtSec(vm.sClipInterval), "1.5")
            "base" -> Triple("Base URL", vm.sBaseUrl, DeepSeekClient.DEFAULT_BASE_URL)
            else -> Triple("正文上限（字）", vm.sMaxChars.toString(), "12000")
        }
        var v by remember(which) { mutableStateOf(init) }
        AlertDialog(
            onDismissRequest = { editing = null }, containerColor = G.c.card,
            title = { Text(title) },
            text = {
                Column {
                    GroupCard(padding = true, modifier = Modifier.background(G.c.fieldBg)) { Field(v, { v = it }, hint, singleLine = true, mono = which !in setOf("chars", "tags", "interval"), minHeight = 24, fontSize = 14) }
                    if (which == "interval") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("0.8", "1", "1.5", "2", "3").forEach { o -> MiniButton(o + " 秒") { v = o } }
                    }
                    if (which == "interval") Text("间隔太短时，部分输入法的剪贴板历史可能只记下最后一条。范围 0.3–5 秒。", fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 8.dp))
                    if (which == "base") Text("使用第三方兼容接口时，请确认其兼容 OpenAI Chat Completions 格式；不保证所有接口都可用。", fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 8.dp))
                    if (which == "key") Text("Key 只加密保存在本机，不会写入日志，也不会随普通备份导出。", fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (which) {
                        "key" -> vm.saveApiKey(v)
                        "jina" -> vm.saveJinaKey(v)
                        "model" -> vm.saveModel(v)
                        "base" -> vm.saveBaseUrl(v)
                        "tags" -> vm.setDefaultTags(v)
                        "interval" -> v.trim().toDoubleOrNull()?.takeIf { it in 0.3..5.0 }?.let { vm.setClipInterval((it * 1000).toLong()) } ?: vm.toast("请输入 0.3–5 之间的秒数")
                        else -> v.trim().toIntOrNull()?.takeIf { it in 1000..100000 }?.let(vm::setMaxChars) ?: vm.toast("请输入 1000–100000 之间的数字")
                    }
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (which == "model" || which == "base") TextButton(onClick = {
                        if (which == "model") vm.saveModel(DeepSeekClient.DEFAULT_MODEL) else vm.saveBaseUrl(DeepSeekClient.DEFAULT_BASE_URL); editing = null
                    }) { Text("恢复默认") }
                    TextButton(onClick = { editing = null }) { Text("取消") }
                }
            },
        )
    }

    if (confirmRestore) com.scarlett.geekradar.ui.ConfirmDialog("恢复默认提示词？", "当前提示词会保留在版本快照中，默认提示词将作为新版本生效。历史记录和 API 配置不会被删除。", "恢复默认",
        onDismiss = { confirmRestore = false }) { vm.restoreDefaultPrompt() }

    if (showSnapshots) AlertDialog(
        onDismissRequest = { showSnapshots = false }, containerColor = G.c.card,
        title = { Text("提示词版本快照") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snaps.forEach { s ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(G.c.fieldBg).padding(10.dp)) {
                        Text("v${s.version} · ${fmtTime(s.createdAt)}" + (if (s.label.isNotBlank()) " · ${s.label}" else "") + if (s.version == vm.sPromptVersion) " · 使用中" else "",
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = G.c.text)
                        Text(s.content.take(120), fontSize = 11.sp, color = G.c.secondary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
                        if (s.version != vm.sPromptVersion) MiniButton("恢复此版本") { vm.restoreSnapshot(s.content); showSnapshots = false }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSnapshots = false }) { Text("关闭") } },
    )

    vm.importPreview?.let { p ->
        var withPrompt by remember { mutableStateOf(false) }
        var withSettings by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { vm.importPreview = null }, containerColor = G.c.card,
            title = { Text("确认导入") },
            text = {
                Column {
                    Text("备份中共有 ${p.data.tasks.size} 条记录：新增 ${p.newCount} 条，与现有记录重复 ${p.dupCount} 条（将跳过，不覆盖）。\n提示词快照 ${p.data.snapshots.size} 个会合并进版本列表。", fontSize = 13.5.sp)
                    if (p.data.prompt != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(withPrompt, { withPrompt = it }); Text("同时把当前提示词替换为备份中的版本（会覆盖当前提示词，旧版本保留在快照中）", fontSize = 12.5.sp)
                    }
                    if (p.data.settings != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(withSettings, { withSettings = it }); Text("同时导入设置（模型名、Base URL、备用通道等，会覆盖当前设置；API Key 不在备份中）", fontSize = 12.5.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.applyImport(withPrompt, withSettings) }) { Text("导入") } },
            dismissButton = { TextButton(onClick = { vm.importPreview = null }) { Text("取消") } },
        )
    }
}

private fun fmtSec(ms: Long): String = if (ms % 1000 == 0L) (ms / 1000).toString() else "%.1f".format(ms / 1000.0)

@Composable
private fun CoverOption(title: String, sub: String, selected: Boolean, thumb: (@Composable () -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = G.c.accent))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = G.c.text)
            Text(sub, fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 2.dp))
        }
        if (thumb != null) { Spacer(Modifier.width(10.dp)); thumb() }
    }
}

/** 封面样例缩略图（用示例标题现场渲染，与实际生成的封面同一套绘制代码）。 */
@Composable
private fun CoverThumb() {
    val img by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { CoverRenderer.preview(CoverLayout.content(27, "让 AI 替你点网页", "一句中文，自动打开网页、点按钮、填表单。", listOf("开源工具", "GitHub开源", "免费")), 180).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(width = 39.dp, height = 52.dp).clip(RoundedCornerShape(6.dp)).background(G.c.fieldBg).border(0.5.dp, G.c.divider, RoundedCornerShape(6.dp))) {
        img?.let { Image(it, "封面样例", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
