package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.core.LinkLocation
import com.scarlett.geekradar.core.UrlTools
import com.scarlett.geekradar.data.LinkEntity
import com.scarlett.geekradar.ui.CategoryTag
import com.scarlett.geekradar.ui.GroupCard
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.RowDivider
import com.scarlett.geekradar.ui.copyText
import com.scarlett.geekradar.ui.openUrl
import com.scarlett.geekradar.ui.theme.G

fun LinkEntity.best(): String = if (UrlTools.isHttpUrl(rawUrl)) rawUrl else resolvedUrl.ifBlank { rawUrl }

/** 单条链接：类别、名称、原始 URL（等宽、完整显示）、解析地址、复制与打开。 */
@Composable
fun LinkItem(l: LinkEntity, onCategory: ((LinkCategory) -> Unit)?) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val cat = LinkCategory.fromName(l.category)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                CategoryTag(cat, onClick = onCategory?.let { { menu = true } })
                DropdownMenu(menu, { menu = false }) {
                    LinkCategory.entries.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = { menu = false; onCategory?.invoke(c) })
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(l.linkId, fontSize = 11.sp, color = G.c.tertiary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(l.name.ifBlank { l.anchorText.ifBlank { UrlTools.hostOf(l.best()) } }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                color = G.c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (l.purpose.isNotBlank()) Text(l.purpose, fontSize = 12.5.sp, color = G.c.secondary, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.padding(top = 5.dp)) {
            SelectionContainer(Modifier.fillMaxWidth()) {
                Column {
                    Text(l.rawUrl, fontSize = 11.5.sp, lineHeight = 16.sp, color = G.c.text.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace)
                    if (l.resolvedUrl.isNotBlank() && l.resolvedUrl != l.rawUrl)
                        Text("解析为：" + l.resolvedUrl, fontSize = 10.5.sp, lineHeight = 15.sp, color = G.c.tertiary, fontFamily = FontFamily.Monospace)
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton("复制") { copyText(ctx, "链接", l.best()) }
                MiniButton("打开") { openUrl(ctx, l.best()) }
            }
        }
        val meta = buildList {
            add(LinkLocation.label(l.sourceLocation))
            if (l.occurrences > 1) add("出现 ${l.occurrences} 次")
            if (l.categoryByAi) add("类别由 AI 建议")
            if (l.userConfirmed) add("已人工确认")
            if (l.programNote.isNotBlank()) add(l.programNote)
            if (l.userNote.isNotBlank()) add("备注：" + l.userNote)
        }
        Text(meta.joinToString(" · "), fontSize = 10.5.sp, color = G.c.tertiary, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 链接分组：主要链接全部展示，页面导航等"仅保存"链接折叠。 */
@Composable
fun LinkList(links: List<LinkEntity>, onCategory: ((LinkEntity, LinkCategory) -> Unit)?, primaryOnly: Boolean = false) {
    val main = links.filter { it.sendToModel || it.category == LinkCategory.SOURCE.name || it.category == LinkCategory.RESOURCE.name }
        .sortedBy { order(it) }
    val rest = links.filter { it !in main }
    var showRest by rememberSaveable { mutableStateOf(false) }
    val shown = if (primaryOnly) main.filter { it.category == LinkCategory.SOURCE.name || it.category == LinkCategory.RESOURCE.name } else main
    GroupCard {
        if (shown.isEmpty()) Text("暂无链接", fontSize = 13.sp, color = G.c.tertiary, modifier = Modifier.padding(14.dp))
        shown.forEachIndexed { i, l ->
            LinkItem(l, onCategory?.let { f -> { c: LinkCategory -> f(l, c) } })
            if (i < shown.size - 1) RowDivider()
        }
        val others = if (primaryOnly) (main - shown.toSet()) + rest else rest
        if (others.isNotEmpty()) {
            RowDivider()
            Text((if (showRest) "收起" else "展开") + "其他链接 ${others.size} 条" + if (primaryOnly) "" else "（网页导航等，仅保存，不发送给模型）",
                fontSize = 12.5.sp, color = G.c.accent, modifier = Modifier.fillMaxWidth().clickable { showRest = !showRest }.padding(14.dp))
            if (showRest) others.sortedBy { order(it) }.forEach { l ->
                RowDivider()
                LinkItem(l, onCategory?.let { f -> { c: LinkCategory -> f(l, c) } })
            }
        }
    }
}

private fun order(l: LinkEntity) = when (l.category) { "SOURCE" -> 0; "RESOURCE" -> 1; "PENDING" -> 2; else -> 3 } * 10000 + l.position
