package com.scarlett.geekradar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.scarlett.geekradar.ui.AppRoot
import com.scarlett.geekradar.ui.AppViewModel
import com.scarlett.geekradar.ui.theme.GeekTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 仅首次创建时处理启动 Intent；重建（旋转/回收恢复）时不重复处理同一分享。
        if (savedInstanceState == null) handleIntent(intent)
        setContent { GeekTheme { AppRoot(vm) } }
    }

    /** singleTask：应用已在运行或在后台时，新的分享从这里进入。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                        ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                    vm.onShared(text, subject)
                }
                Intent.ACTION_PROCESS_TEXT -> vm.onShared(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(), null)
            }
        } catch (e: Exception) {
            vm.toast("无法读取分享内容：${e.message ?: "格式异常"}")
        }
    }
}
