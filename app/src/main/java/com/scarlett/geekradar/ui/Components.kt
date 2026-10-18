package com.scarlett.geekradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.LinkCategory
import com.scarlett.geekradar.ui.theme.G

/** 大标题毛玻璃顶栏（苹果式），可带返回与右侧操作。 */
@Composable
fun LargeTitleBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(G.c.bar).windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = G.c.accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = G.c.text, letterSpacing = 0.5.sp)
    }
    HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
}

/** 紧凑顶栏：返回 + 居中标题(+副标题) + 右侧操作。 */
@Composable
fun SmallTitleBar(title: String, subtitle: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(G.c.bar).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = G.c.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.c.text)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = G.c.secondary)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
    HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
}

@Composable
fun SectionLabel(text: String, hint: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = G.c.secondary)
        if (hint != null) { Spacer(Modifier.width(8.dp)); Text(hint, fontSize = 11.5.sp, color = G.c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)) }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, padding: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.c.card)
            .then(if (padding) Modifier.padding(14.dp) else Modifier),
        content = content,
    )
}

@Composable
fun RowDivider() = HorizontalDivider(thickness = 0.5.dp, color = G.c.divider, modifier = Modifier.padding(start = 14.dp))

@Composable
fun SettingRow(icon: ImageVector?, title: String, sub: String? = null, value: String? = null, mono: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(G.c.accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = G.c.text)
            if (sub != null) Text(sub, fontSize = 12.sp, color = G.c.secondary, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, fontSize = 13.sp, color = G.c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, modifier = Modifier.padding(start = 8.dp).widthIn(max = 170.dp))
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = G.c.tertiary, modifier = Modifier.size(20.dp))
    }
}


@Composable
fun CategoryTag(cat: LinkCategory, onClick: (() -> Unit)? = null) {
    val (fg, bg) = when (cat) {
        LinkCategory.SOURCE -> G.c.secondary to G.c.chipNeutral
        LinkCategory.RESOURCE -> G.c.accent to G.c.accentSoft
        LinkCategory.AUX -> G.c.ok to G.c.ok.copy(alpha = 0.12f)
        LinkCategory.PENDING -> G.c.warn to G.c.warnSoft
    }
    Text(
        cat.label + if (onClick != null) " ▾" else "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Chip(text: String, fg: Color = G.c.accent, bg: Color = G.c.accentSoft, onClick: (() -> Unit)? = null) {
    Text(text, fontSize = 11.5.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun MiniButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(text, fontSize = 12.5.sp, color = if (enabled) G.c.accent else G.c.tertiary, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(G.c.fieldBg).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.height(50.dp).shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(16.dp), ambientColor = G.c.accent, spotColor = G.c.accent)
            .clip(RoundedCornerShape(16.dp)).background(if (enabled) G.c.accent else G.c.tertiary)
            .clickable(enabled = enabled && !loading, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(text, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BarIconButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (enabled) G.c.accent else G.c.tertiary, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.5.sp, color = if (enabled) G.c.accent else G.c.tertiary)
    }
}

/** 底部毛玻璃操作栏容器。 */
@Composable
fun GlassBottomBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(G.c.bar)) {
        HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content,
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(G.c.chipNeutral).padding(2.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(o, fontSize = 12.5.sp, color = G.c.text.copy(alpha = if (on) 1f else 0.7f), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).then(if (on) Modifier.background(G.c.card) else Modifier)
                    .clickable { onSelect(o) }.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

/** 统一风格的多行输入框。 */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    minHeight: Int = 44, singleLine: Boolean = false, mono: Boolean = false, fontSize: Int = 15, bordered: Boolean = false,
) {
    val c = G.c
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = TextStyle(color = c.text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.6).sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
        cursorBrush = SolidColor(c.accent),
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp)
            .then(if (bordered) Modifier.border(3.dp, c.accentSoft, RoundedCornerShape(18.dp)) else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = c.tertiary, fontSize = fontSize.sp, lineHeight = (fontSize * 1.6).sp)
                inner()
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "确认", danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = if (danger) G.c.danger else G.c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = G.c.card,
    )
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

@Composable
fun NoticeCard(title: String, lines: List<String>, color: Color = G.c.warn, bg: Color = G.c.warnSoft, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        lines.forEach { Text("· $it", fontSize = 12.5.sp, lineHeight = 19.sp, color = G.c.text.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp)) }
        content()
    }
}
