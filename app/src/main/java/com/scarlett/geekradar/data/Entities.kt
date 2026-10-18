package com.scarlett.geekradar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一次"链接/原文 → 文案"任务。抓取到的原文与 AI 结果分字段存储。 */
@Entity(tableName = "tasks", indices = [Index(value = ["uuid"], unique = true), Index("updatedAt")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** url / text / share */
    val inputType: String,
    /** 用户最初输入或分享的完整原始文本（永不丢弃）。 */
    val rawInput: String,
    /** 主原始来源链接（原样字符串），可为空。 */
    val originalUrl: String? = null,
    val finalPageUrl: String = "",
    val pageTitle: String = "",
    val pageDescription: String = "",
    /** 抓取到的正文或用户粘贴的原文。 */
    val sourceText: String = "",
    val sourceEdited: Boolean = false,
    /** NONE / OK / PARTIAL / FAILED / MANUAL */
    val fetchStatus: String = "NONE",
    /** local / jina / manual / none */
    val fetchChannel: String = "none",
    val fetchError: String = "",
    val fetchedAt: Long = 0,
    val style: String = "种草分享",
    val length: String = "中",
    // ---- AI 生成结果（与原文分离）----
    val titleCandidatesJson: String = "[]",
    val selectedTitle: Int = 0,
    val generatedBody: String = "",
    val rawModelOutput: String = "",
    val structuredOk: Boolean = false,
    val factChecksJson: String = "[]",
    val uncertainJson: String = "[]",
    val warningsJson: String = "[]",
    /** 模型本次生成给出的标签（JSON 数组）。1.1.0 新增。 */
    val generatedTagsJson: String = "[]",
    /** 用户保留修改时，新一次生成的正文暂存于此供参考。 */
    val pendingBody: String = "",
    // ---- 用户最终文案 ----
    val finalTitle: String = "",
    val finalBody: String = "",
    /** 用户最终的话题标签（JSON 字符串数组，不带 #）。1.1.0 新增，数据库 v2。 */
    val tagsJson: String = "[]",
    val userEdited: Boolean = false,
    val manuallySaved: Boolean = false,
    // ---- 生成元数据 ----
    /** NONE / RUNNING / DONE / ERROR / INTERRUPTED */
    val genStatus: String = "NONE",
    val genError: String = "",
    val generatedAt: Long = 0,
    val modelName: String = "",
    val promptVersion: Int = 0,
    val promptSnapshot: String = "",
    val note: String = "",
)

/** 链接清单：程序独立提取与保存，AI 只能通过 linkId 给出名称/用途/类别建议。 */
@Entity(
    tableName = "links",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** 内部 ID，如 L1。 */
    val linkId: String,
    /** 原始 URL 字符串（以此为准，永不覆盖）。 */
    val rawUrl: String,
    /** 解析后的绝对 URL（相对链接时与原始值不同）。 */
    val resolvedUrl: String,
    /** 仅用于匹配的规范化值。 */
    val normalizedUrl: String,
    val sourceLocation: String,
    /** SOURCE / RESOURCE / AUX / PENDING */
    val category: String,
    val anchorText: String = "",
    val name: String = "",
    val purpose: String = "",
    val userNote: String = "",
    val programNote: String = "",
    val occurrences: Int = 1,
    val sendToModel: Boolean = true,
    val categoryByAi: Boolean = false,
    val userConfirmed: Boolean = false,
    /** 是否已验证（程序不会自动标记为已验证）。 */
    val verified: Boolean = false,
    val position: Int = 0,
)

@Entity(tableName = "prompt_snapshots")
data class PromptSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: Int,
    val content: String,
    val createdAt: Long,
    val label: String = "",
)
