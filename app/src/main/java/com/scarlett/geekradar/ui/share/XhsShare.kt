package com.scarlett.geekradar.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import java.io.File

/** 与小红书的交互：只写剪贴板、启动小红书或把封面图分享给它；不做任何自动发布。 */
object XhsShare {
    const val PACKAGE = "com.xingin.xhs"

    fun isInstalled(ctx: Context): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun writeClip(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= 24) clip.description.extras = PersistableBundle().apply { putBoolean("geekradar", true) }
        cm.setPrimaryClip(clip)
    }

    sealed class LaunchResult {
        data object OpenedApp : LaunchResult()
        data object SharedToXhs : LaunchResult()
        data object Chooser : LaunchResult()
        data class Failed(val message: String) : LaunchResult()
    }

    /** 方式①：只启动小红书。 */
    fun openApp(ctx: Context): LaunchResult {
        val i = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return LaunchResult.Failed("未安装小红书（com.xingin.xhs），请先安装；文案已在剪贴板中")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); LaunchResult.OpenedApp } catch (e: Exception) { LaunchResult.Failed("无法打开小红书：${e.message}") }
    }

    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /** 方式②：把封面图以 ACTION_SEND image/png 分享给小红书；失败时回退到系统分享选择器。 */
    fun shareCover(ctx: Context, uri: Uri): LaunchResult {
        fun base() = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData(ClipDescription("cover", arrayOf("image/png")), ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (isInstalled(ctx)) {
            try { ctx.startActivity(base().setPackage(PACKAGE)); return LaunchResult.SharedToXhs } catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
        }
        return try {
            val chooser = Intent.createChooser(base(), "分享封面到…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(chooser); LaunchResult.Chooser
        } catch (e: Exception) { LaunchResult.Failed("无法打开分享面板：${e.message}") }
    }
}
