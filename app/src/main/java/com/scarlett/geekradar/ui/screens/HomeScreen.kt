package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.PromptBuilder
import com.scarlett.geekradar.core.UrlTools
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.Chip
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.LargeTitleBar
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.NoticeCard
import com.scarlett.geekradar.ui.PrimaryButton
import com.scarlett.geekradar.ui.Route
import com.scarlett.geekradar.ui.Segmented
import com.scarlett.geekradar.ui.fmtTime
import com.scarlett.geekradar.ui.readClipboard
import com.scarlett.geekradar.ui.theme.G

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val recent by vm.history.collectAsState()
    val a = UrlTools.analyze(vm.homeInput)
    Column(Modifier.fillMaxSize()) {
        LargeTitleBar("极客雷达")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            Text("今天发现了什么好东西？", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = G.c.secondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 10.dp))
            Column(
                Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = G.c.text.copy(alpha = 0.15f), spotColor = G.c.text.copy(alpha = 0.15f))
                    .clip(RoundedCornerShape(22.dp)).background(G.c.card).padding(18.dp)
            ) {
                Field(vm.homeInput, { vm.setHome(it) }, "粘贴网页链接，或直接粘贴文章原文…\n\n支持 X、公众号、GitHub、技术博客、产品官网等", minHeight = 130, fontSize = 16)
                Spacer(Modifier.height(10.dp))
                when {
                    a.urls.size == 1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(if (a.looksLikeArticle) "📝 原文 · 含 1 个链接" else "🔗 已识别链接")
                        Spacer(Modifier.width(8.dp))
                        Text(UrlTools.hostOf(a.urls[0]), fontSize = 12.sp, color = G.c.tertiary, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    a.urls.size > 1 -> Column {
                        Text(if (a.looksLikeArticle) "📝 原文，含 ${a.urls.size} 个链接（全部保存）" else "🔗 识别到 ${a.urls.size} 个链接，点选要解析的主链接（其余一并保存）：",
                            fontSize = 12.sp, color = G.c.secondary)
                        if (!a.looksLikeArticle) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            a.urls.forEachIndexed { i, u ->
                                val on = i == vm.homeMainIndex
                                Chip((if (on) "● " else "○ ") + u.take(48), fg = if (on) G.c.accent else G.c.secondary,
                                    bg = if (on) G.c.accentSoft else G.c.chipNeutral) { vm.homeMainIndex = i }
                            }
                        }
                    }
                    a.kind == UrlTools.InputKind.TEXT -> Chip("📝 原文 · ${vm.homeInput.trim().length} 字")
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniButton("粘贴") { val c = readClipboard(ctx); if (c.isBlank()) vm.toast("剪贴板为空") else vm.setHome(if (vm.homeInput.isBlank()) c else vm.homeInput + "\n" + c) }
                    Spacer(Modifier.width(8.dp))
                    if (vm.homeInput.isNotEmpty()) MiniButton("清空") { vm.setHome("") }
                }
            }
            vm.homeNotice?.let { Spacer(Modifier.height(10.dp)); NoticeCard("请确认", listOf(it)) }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("风格", fontSize = 13.sp, color = G.c.secondary, modifier = Modifier.width(40.dp))
                Segmented(PromptBuilder.STYLES, vm.style) { vm.style = it; vm.setDefaults(it, null) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("长度", fontSize = 13.sp, color = G.c.secondary, modifier = Modifier.width(40.dp))
                Segmented(PromptBuilder.LENGTHS, vm.length) { vm.length = it; vm.setDefaults(null, it) }
            }
            PrimaryButton(if (a.urls.isNotEmpty() && !a.looksLikeArticle) "开始解析" else "下一步", Modifier.fillMaxWidth().padding(top = 16.dp), enabled = a.kind != UrlTools.InputKind.EMPTY) { vm.start() }

            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("最近记录", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = G.c.text)
                Spacer(Modifier.weight(1f))
                Text("全部 ›", fontSize = 13.sp, color = G.c.accent, modifier = Modifier.clickable { vm.switchTab(Route.HISTORY) })
            }
            if (recent.isEmpty()) Text("还没有记录。分享或粘贴一个链接试试。", fontSize = 13.sp, color = G.c.tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 24.dp))
            else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.c.card).padding(horizontal = 16.dp)) {
                recent.take(4).forEachIndexed { i, t ->
                    Column(Modifier.fillMaxWidth().clickable { vm.push(Route(if (t.genStatus == "DONE" || t.finalBody.isNotBlank()) Route.RESULT else Route.PROCESS, t.id)) }.padding(vertical = 12.dp)) {
                        Text(t.finalTitle.ifBlank { t.pageTitle.ifBlank { t.rawInput.take(40) } }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = G.c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((t.originalUrl ?: "粘贴原文") + " · " + fmtTime(t.updatedAt), fontSize = 11.5.sp, color = G.c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                    if (i < minOf(3, recent.size - 1)) androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
