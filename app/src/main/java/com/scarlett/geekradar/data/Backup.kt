package com.scarlett.geekradar.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 完整备份（不含 API Key）。
 * 格式：{format:"geekradar-backup", version:1, tasks:[{...task, links:[...]}], prompt:{...}, promptSnapshots:[...], settings:{...}}
 */
object Backup {
    const val FORMAT = "geekradar-backup"
    const val VERSION = 1

    data class Data(
        val tasks: List<Pair<TaskEntity, List<LinkEntity>>>,
        val prompt: String?,
        val promptVersion: Int,
        val snapshots: List<PromptSnapshotEntity>,
        val settings: JSONObject?,
        val exportedAt: Long,
    )

    fun export(tasks: List<TaskEntity>, links: List<LinkEntity>, prompt: String, promptVersion: Int,
               snapshots: List<PromptSnapshotEntity>, settings: Map<String, Any>, appVersion: String): String {
        val byTask = links.groupBy { it.taskId }
        val root = JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("appVersion", appVersion)
            .put("exportedAt", System.currentTimeMillis())
            .put("note", "不含 API Key")
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(taskToJson(t).put("links", JSONArray().apply { (byTask[t.id] ?: emptyList()).forEach { put(linkToJson(it)) } }))
        }
        root.put("tasks", arr)
        root.put("prompt", JSONObject().put("content", prompt).put("version", promptVersion))
        root.put("promptSnapshots", JSONArray().apply {
            snapshots.forEach { put(JSONObject().put("version", it.version).put("content", it.content).put("createdAt", it.createdAt).put("label", it.label)) }
        })
        root.put("settings", JSONObject(settings))
        return root.toString(2)
    }

    /** 解析并校验备份；任何问题抛出 IllegalArgumentException（带中文说明），不会触碰数据库。 */
    fun parse(text: String): Data {
        val root = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("不是有效的 JSON 文件") }
        if (root.optString("format") != FORMAT) throw IllegalArgumentException("不是极客雷达的备份文件（缺少 format 标记）")
        val v = root.optInt("version", -1)
        if (v < 1 || v > VERSION) throw IllegalArgumentException("不支持的备份版本：$v")
        val tasksArr = root.optJSONArray("tasks") ?: throw IllegalArgumentException("备份中缺少 tasks 字段")
        val tasks = (0 until tasksArr.length()).map { i ->
            val o = tasksArr.optJSONObject(i) ?: throw IllegalArgumentException("第 ${i + 1} 条记录格式错误")
            val t = taskFromJson(o, i)
            val la = o.optJSONArray("links") ?: JSONArray()
            t to (0 until la.length()).map { j -> linkFromJson(la.getJSONObject(j), i, j) }
        }
        val p = root.optJSONObject("prompt")
        val sa = root.optJSONArray("promptSnapshots") ?: JSONArray()
        val snaps = (0 until sa.length()).map { i ->
            val o = sa.getJSONObject(i)
            PromptSnapshotEntity(version = o.optInt("version"), content = o.optString("content"), createdAt = o.optLong("createdAt"), label = o.optString("label"))
        }.filter { it.content.isNotBlank() }
        return Data(tasks, p?.optString("content")?.takeIf { it.isNotBlank() }, p?.optInt("version") ?: 0, snaps,
            root.optJSONObject("settings"), root.optLong("exportedAt"))
    }

    fun taskToJson(t: TaskEntity): JSONObject = JSONObject()
        .put("uuid", t.uuid).put("createdAt", t.createdAt).put("updatedAt", t.updatedAt).put("inputType", t.inputType)
        .put("rawInput", t.rawInput).put("originalUrl", t.originalUrl ?: JSONObject.NULL).put("finalPageUrl", t.finalPageUrl)
        .put("pageTitle", t.pageTitle).put("pageDescription", t.pageDescription).put("sourceText", t.sourceText)
        .put("sourceEdited", t.sourceEdited).put("fetchStatus", t.fetchStatus).put("fetchChannel", t.fetchChannel)
        .put("fetchError", t.fetchError).put("fetchedAt", t.fetchedAt).put("style", t.style).put("length", t.length)
        .put("titleCandidates", JSONArray(t.titleCandidatesJson)).put("selectedTitle", t.selectedTitle)
        .put("generatedBody", t.generatedBody).put("rawModelOutput", t.rawModelOutput).put("structuredOk", t.structuredOk)
        .put("factChecks", JSONArray(t.factChecksJson)).put("uncertain", JSONArray(t.uncertainJson)).put("warnings", JSONArray(t.warningsJson))
        .put("pendingBody", t.pendingBody).put("finalTitle", t.finalTitle).put("finalBody", t.finalBody)
        .put("userEdited", t.userEdited).put("manuallySaved", t.manuallySaved).put("genStatus", t.genStatus).put("genError", t.genError)
        .put("generatedAt", t.generatedAt).put("modelName", t.modelName).put("promptVersion", t.promptVersion)
        .put("promptSnapshot", t.promptSnapshot).put("note", t.note)

    private fun taskFromJson(o: JSONObject, i: Int): TaskEntity {
        val uuid = o.optString("uuid").ifBlank { throw IllegalArgumentException("第 ${i + 1} 条记录缺少 uuid") }
        val status = o.optString("genStatus", "NONE").let { if (it == "RUNNING") "INTERRUPTED" else it }
        return TaskEntity(
            uuid = uuid, createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"), inputType = o.optString("inputType", "text"),
            rawInput = o.optString("rawInput"), originalUrl = if (o.isNull("originalUrl")) null else o.optString("originalUrl"),
            finalPageUrl = o.optString("finalPageUrl"), pageTitle = o.optString("pageTitle"), pageDescription = o.optString("pageDescription"),
            sourceText = o.optString("sourceText"), sourceEdited = o.optBoolean("sourceEdited"), fetchStatus = o.optString("fetchStatus", "NONE"),
            fetchChannel = o.optString("fetchChannel", "none"), fetchError = o.optString("fetchError"), fetchedAt = o.optLong("fetchedAt"),
            style = o.optString("style", "种草分享"), length = o.optString("length", "中"),
            titleCandidatesJson = (o.optJSONArray("titleCandidates") ?: JSONArray()).toString(), selectedTitle = o.optInt("selectedTitle"),
            generatedBody = o.optString("generatedBody"), rawModelOutput = o.optString("rawModelOutput"), structuredOk = o.optBoolean("structuredOk"),
            factChecksJson = (o.optJSONArray("factChecks") ?: JSONArray()).toString(), uncertainJson = (o.optJSONArray("uncertain") ?: JSONArray()).toString(),
            warningsJson = (o.optJSONArray("warnings") ?: JSONArray()).toString(), pendingBody = o.optString("pendingBody"),
            finalTitle = o.optString("finalTitle"), finalBody = o.optString("finalBody"), userEdited = o.optBoolean("userEdited"),
            manuallySaved = o.optBoolean("manuallySaved"), genStatus = status, genError = o.optString("genError"),
            generatedAt = o.optLong("generatedAt"), modelName = o.optString("modelName"), promptVersion = o.optInt("promptVersion"),
            promptSnapshot = o.optString("promptSnapshot"), note = o.optString("note"),
        )
    }

    fun linkToJson(l: LinkEntity): JSONObject = JSONObject()
        .put("linkId", l.linkId).put("rawUrl", l.rawUrl).put("resolvedUrl", l.resolvedUrl).put("normalizedUrl", l.normalizedUrl)
        .put("sourceLocation", l.sourceLocation).put("category", l.category).put("anchorText", l.anchorText).put("name", l.name)
        .put("purpose", l.purpose).put("userNote", l.userNote).put("programNote", l.programNote).put("occurrences", l.occurrences)
        .put("sendToModel", l.sendToModel).put("categoryByAi", l.categoryByAi).put("userConfirmed", l.userConfirmed)
        .put("verified", l.verified).put("position", l.position)

    private fun linkFromJson(o: JSONObject, i: Int, j: Int): LinkEntity {
        val raw = o.optString("rawUrl")
        if (raw.isEmpty()) throw IllegalArgumentException("第 ${i + 1} 条记录的第 ${j + 1} 个链接缺少 rawUrl")
        return LinkEntity(
            taskId = 0, linkId = o.optString("linkId", "L${j + 1}"), rawUrl = raw, resolvedUrl = o.optString("resolvedUrl", raw),
            normalizedUrl = o.optString("normalizedUrl"), sourceLocation = o.optString("sourceLocation"), category = o.optString("category", "PENDING"),
            anchorText = o.optString("anchorText"), name = o.optString("name"), purpose = o.optString("purpose"), userNote = o.optString("userNote"),
            programNote = o.optString("programNote"), occurrences = o.optInt("occurrences", 1), sendToModel = o.optBoolean("sendToModel", true),
            categoryByAi = o.optBoolean("categoryByAi"), userConfirmed = o.optBoolean("userConfirmed"), verified = o.optBoolean("verified"),
            position = o.optInt("position", j),
        )
    }
}
