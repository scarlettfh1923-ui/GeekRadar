package com.scarlett.geekradar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scarlett.geekradar.core.PromptBuilder
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.ConfirmDialog
import com.scarlett.geekradar.ui.Field
import com.scarlett.geekradar.ui.GlassBottomBar
import com.scarlett.geekradar.ui.GroupCard
import com.scarlett.geekradar.ui.MiniButton
import com.scarlett.geekradar.ui.PrimaryButton
import com.scarlett.geekradar.ui.SectionLabel
import com.scarlett.geekradar.ui.SmallTitleBar
import com.scarlett.geekradar.ui.theme.G

@Composable
fun PromptEditorScreen(vm: AppViewModel) {
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val history by vm.history.collectAsState()
    val dirty = vm.promptDraft != vm.sPrompt
    Column(Modifier.fillMaxSize()) {
        SmallTitleBar("编辑提示词", subtitle = if (dirty) "有未保存的修改" else "v${vm.sPromptVersion} · 已保存", onBack = { if (dirty) confirmLeave = true else vm.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp)) {
            SectionLabel("系统提示词", "${vm.promptDraft.length} 字 · 保存后立即对新的生成生效")
            GroupCard(padding = true) { Field(vm.promptDraft, vm::setDraft, "输入提示词", minHeight = 320, mono = true, fontSize = 13) }
            Text("程序会在提示词后自动附加一段固定安全约束（网页内容只作数据、链接只用 ID 引用），并在用户消息中提供网页标题、正文与带编号的链接清单。若改成非 JSON 结构，结果页会原样展示生成内容供你编辑。",
                fontSize = 11.5.sp, color = G.c.tertiary, modifier = Modifier.padding(6.dp))
            SectionLabel("固定安全约束（只读）")
            GroupCard(padding = true) { Text(PromptBuilder.SAFETY_SUFFIX.trim(), fontSize = 11.5.sp, color = G.c.secondary, fontFamily = FontFamily.Monospace) }

            SectionLabel("测试", "使用编辑器中当前的文本（未保存也可以）")
            GroupCard(padding = true) {
                val sample = history.firstOrNull { it.sourceText.isNotBlank() }
                Text(if (vm.promptTestTaskId > 0) "测试素材：所选历史记录" else "测试素材：内置示例（browser-use 项目）", fontSize = 13.sp, color = G.c.text)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton("用内置示例") { vm.promptTestTaskId = 0 }
                    if (sample != null) MiniButton("用最近一条记录") { vm.promptTestTaskId = sample.id }
                }
                vm.promptTestResult?.let {
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = G.c.text) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        GlassBottomBar {
            MiniButton("恢复默认") { confirmRestore = true }
            MiniButton(if (vm.promptTesting) "测试中…" else "测试", enabled = !vm.promptTesting) { vm.testPromptDraft() }
            PrimaryButton("保存", Modifier.weight(1f), enabled = dirty) { vm.savePromptDraft() }
        }
    }
    if (confirmRestore) ConfirmDialog("恢复默认提示词？", "默认提示词将作为新版本生效，当前版本保留在快照中。历史记录与 API 配置不受影响。", "恢复默认",
        onDismiss = { confirmRestore = false }) { vm.restoreDefaultPrompt() }
    if (confirmLeave) ConfirmDialog("放弃未保存的修改？", "离开后，编辑器中未保存的提示词修改将丢失。", "放弃修改", danger = true,
        onDismiss = { confirmLeave = false }) { vm.setDraft(vm.sPrompt); vm.pop() }
}
