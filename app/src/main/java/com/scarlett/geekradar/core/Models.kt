package com.scarlett.geekradar.core

/** 链接类别（任务书 6.1）。 */
enum class LinkCategory(val label: String, val modelKey: String) {
    SOURCE("原始来源", "source"),
    RESOURCE("资源", "resource"),
    AUX("辅助", "auxiliary"),
    PENDING("待确认", "pending");

    companion object {
        fun fromName(n: String?): LinkCategory = entries.firstOrNull { it.name == n } ?: PENDING
        fun fromModelKey(k: String?): LinkCategory? {
            val s = k?.trim()?.lowercase() ?: return null
            return when (s) {
                "resource", "资源", "资源链接" -> RESOURCE
                "auxiliary", "aux", "辅助", "辅助链接", "reference" -> AUX
                "pending", "unknown", "待确认" -> PENDING
                "source", "原始来源" -> SOURCE
                else -> null
            }
        }
    }
}

/** 来源位置。 */
object LinkLocation {
    const val INPUT = "input"        // 用户分享 / 粘贴的链接
    const val TEXT = "text"          // 用户粘贴原文中出现的链接
    const val CONTENT = "content"    // 网页正文区域
    const val PAGE = "page"          // 网页其他区域（导航、侧栏、页脚等）
    const val JINA = "jina"          // 备用读取通道 Jina Reader 返回的内容
    fun label(l: String) = when (l) {
        INPUT -> "用户输入"; TEXT -> "粘贴原文"; CONTENT -> "网页正文"; PAGE -> "网页其他区域"; JINA -> "备用通道正文"; else -> l
    }
}

/** 从网页 / 文本中直接提取的链接，raw 为原始字符串，resolved 为解析后的绝对地址。 */
data class ExtractedLink(
    val raw: String,
    val resolved: String,
    val anchor: String,
    val location: String,
)

/** 程序编号、分类后的链接（入库前）。 */
data class ClassifiedLink(
    val linkId: String,
    val raw: String,
    val resolved: String,
    val normalized: String,
    val location: String,
    val category: LinkCategory,
    val anchor: String,
    val occurrences: Int = 1,
    val note: String = "",
    /** 是否把这条链接提供给模型分析（页面导航类链接只保存不发送）。所有链接都会入库。 */
    val sendToModel: Boolean = true,
)
