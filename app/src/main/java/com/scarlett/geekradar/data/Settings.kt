package com.scarlett.geekradar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scarlett.geekradar.core.DeepSeekClient
import com.scarlett.geekradar.core.DefaultPrompt

/** 普通设置（不含 API Key），本地 SharedPreferences。 */
class SettingsStore(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("geekradar_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = p.getString("baseUrl", DeepSeekClient.DEFAULT_BASE_URL)!!
        set(v) = p.edit().putString("baseUrl", v.trim()).apply()
    var model: String
        get() = p.getString("model", DeepSeekClient.DEFAULT_MODEL)!!
        set(v) = p.edit().putString("model", v.trim()).apply()
    var temperature: Float
        get() = p.getFloat("temperature", 0.7f)
        set(v) = p.edit().putFloat("temperature", v).apply()
    /** 备用读取通道 Jina Reader，按用户要求默认开启。 */
    var jinaEnabled: Boolean
        get() = p.getBoolean("jinaEnabled", true)
        set(v) = p.edit().putBoolean("jinaEnabled", v).apply()
    var maxChars: Int
        get() = p.getInt("maxChars", 12000)
        set(v) = p.edit().putInt("maxChars", v).apply()
    var defaultStyle: String
        get() = p.getString("defaultStyle", "种草分享")!!
        set(v) = p.edit().putString("defaultStyle", v).apply()
    var defaultLength: String
        get() = p.getString("defaultLength", "中")!!
        set(v) = p.edit().putString("defaultLength", v).apply()

    /** 当前有效提示词与版本号。 */
    var prompt: String
        get() = p.getString("prompt", null) ?: DefaultPrompt.TEXT
        set(v) = p.edit().putString("prompt", v).apply()
    var promptVersion: Int
        get() = p.getInt("promptVersion", 1)
        set(v) = p.edit().putInt("promptVersion", v).apply()
    var promptUpdatedAt: Long
        get() = p.getLong("promptUpdatedAt", 0)
        set(v) = p.edit().putLong("promptUpdatedAt", v).apply()
    var promptInitialized: Boolean
        get() = p.getBoolean("promptInit", false)
        set(v) = p.edit().putBoolean("promptInit", v).apply()

    /** 已应用到哪一版内置默认提示词（见 DefaultPrompt.REVISION）。1.0.0 没有这个键，按 1 处理。 */
    var defaultPromptRevision: Int
        get() = p.getInt("defaultPromptRev", 1)
        set(v) = p.edit().putInt("defaultPromptRev", v).apply()
    /** 1.1.0 一次性链接重分类（作者主页 / 帖子 → 来源）是否已执行。 */
    var linksReclassifiedV2: Boolean
        get() = p.getBoolean("linksReclassV2", false)
        set(v) = p.edit().putBoolean("linksReclassV2", v).apply()

    // ---------------- 分享到小红书（1.1.0） ----------------
    /** 封面方式：COVER_XHS = 使用小红书自带文字配图；COVER_GENERATED = 应用内生成封面图。 */
    var coverMode: String
        get() = p.getString("coverMode", COVER_GENERATED)!!.let { if (it == COVER_XHS) COVER_XHS else COVER_GENERATED }
        set(v) = p.edit().putString("coverMode", v).apply()
    /** 依次写入剪贴板（标题 → 正文 → 标签）的间隔，毫秒。 */
    var clipIntervalMs: Long
        get() = p.getLong("clipIntervalMs", 1500L).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(v) = p.edit().putLong("clipIntervalMs", v.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()
    /** 默认标签（空格分隔，不带 #），每次生成后自动追加。 */
    var defaultTags: String
        get() = p.getString("defaultTags", "")!!
        set(v) = p.edit().putString("defaultTags", v.trim()).apply()
    var autoOpenXhs: Boolean
        get() = p.getBoolean("autoOpenXhs", true)
        set(v) = p.edit().putBoolean("autoOpenXhs", v).apply()

    fun exportable(): Map<String, Any> = mapOf(
        "baseUrl" to baseUrl, "model" to model, "temperature" to temperature.toDouble(), "jinaEnabled" to jinaEnabled,
        "maxChars" to maxChars, "defaultStyle" to defaultStyle, "defaultLength" to defaultLength,
        "coverMode" to coverMode, "clipIntervalMs" to clipIntervalMs, "defaultTags" to defaultTags, "autoOpenXhs" to autoOpenXhs,
    )

    companion object {
        const val COVER_XHS = "xhs"
        const val COVER_GENERATED = "generated"
        const val MIN_INTERVAL = 300L
        const val MAX_INTERVAL = 5000L
    }
}

/**
 * API Key 安全存储：EncryptedSharedPreferences（AES256-GCM，主密钥保存在 Android Keystore）。
 * 若加密存储不可用，不会退回明文存储，而是报告不可用。
 */
class SecureKeyStore(private val ctx: Context) {
    private val prefs: SharedPreferences? = open()
    val available: Boolean get() = prefs != null

    private fun open(): SharedPreferences? = runCatching { create() }.recoverCatching {
        // Keystore 与加密文件不一致（如数据被还原）时，清除损坏的加密文件后重建。
        ctx.deleteSharedPreferences("geekradar_secure")
        create()
    }.getOrNull()

    private fun create(): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "geekradar_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var apiKey: String
        get() = prefs?.getString("apiKey", "") ?: ""
        set(v) { prefs?.edit()?.putString("apiKey", v.trim())?.apply() }

    var jinaKey: String
        get() = prefs?.getString("jinaKey", "") ?: ""
        set(v) { prefs?.edit()?.putString("jinaKey", v.trim())?.apply() }

    fun masked(): String {
        val k = apiKey
        if (k.isBlank()) return "未设置"
        return if (k.length <= 8) "••••" else k.take(3) + "••••••" + k.takeLast(4)
    }
}
