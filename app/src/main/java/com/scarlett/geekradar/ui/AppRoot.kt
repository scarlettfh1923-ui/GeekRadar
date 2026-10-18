package com.scarlett.geekradar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.ui.screens.HistoryScreen
import com.scarlett.geekradar.ui.screens.HomeScreen
import com.scarlett.geekradar.ui.screens.ProcessScreen
import com.scarlett.geekradar.ui.screens.PromptEditorScreen
import com.scarlett.geekradar.ui.screens.ResultScreen
import com.scarlett.geekradar.ui.screens.SettingsScreen
import com.scarlett.geekradar.ui.theme.G

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
    val route = vm.current
    BackHandler(enabled = vm.stack.size > 1) {
        if (route.name == Route.RESULT) vm.flushEditorNow()
        if (route.name == Route.PROCESS) vm.flushSourceNow()
        vm.pop()
    }
    Column(Modifier.fillMaxSize().background(G.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (route.name) {
                Route.HOME -> HomeScreen(vm)
                Route.PROCESS -> ProcessScreen(vm, route.taskId)
                Route.RESULT -> ResultScreen(vm, route.taskId)
                Route.HISTORY -> HistoryScreen(vm)
                Route.SETTINGS -> SettingsScreen(vm)
                Route.PROMPT -> PromptEditorScreen(vm)
                else -> HomeScreen(vm)
            }
        }
        if (route.name in Route.TOP) BottomTabs(route.name) { vm.switchTab(it) }
    }
}

@Composable
private fun BottomTabs(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(G.c.bar)) {
        HorizontalDivider(thickness = 0.5.dp, color = G.c.divider)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Tab(Icons.Filled.Home, "首页", current == Route.HOME) { onSelect(Route.HOME) }
            Tab(Icons.AutoMirrored.Filled.List, "历史", current == Route.HISTORY) { onSelect(Route.HISTORY) }
            Tab(Icons.Filled.Settings, "设置", current == Route.SETTINGS) { onSelect(Route.SETTINGS) }
        }
    }
}

@Composable
private fun Tab(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val color = if (on) G.c.accent else G.c.tertiary
    Column(Modifier.width(84.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "已复制$label", Toast.LENGTH_SHORT).show()
}

fun readClipboard(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
}

/** 由用户主动点击触发；原样使用 URL 字符串打开，优先 Custom Tabs。 */
fun openUrl(ctx: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, Uri.parse(url.trim()))
    } catch (_: Exception) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(ctx, "无法打开链接：${e.message}", Toast.LENGTH_SHORT).show() }
    }
}

fun fmtTime(ms: Long): String = if (ms <= 0) "—" else java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(ms))
fun fmtDay(ms: Long): String = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA).format(java.util.Date(ms))
