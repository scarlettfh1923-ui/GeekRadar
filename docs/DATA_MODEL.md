# 数据结构说明（Room，数据库 geekradar.db，version 2）

## tasks（一次任务）
id, uuid（备份去重用）, createdAt, updatedAt, inputType(url/text/share), rawInput（完整原始输入，永不丢弃）,
originalUrl（主来源原样字符串）, finalPageUrl, pageTitle, pageDescription, sourceText（抓取或粘贴的原文）, sourceEdited,
fetchStatus(NONE/OK/PARTIAL/FAILED/MANUAL), fetchChannel(local/jina/manual/none), fetchError, fetchedAt, style, length,
—— AI 结果（与原文分离）：titleCandidatesJson, selectedTitle, generatedBody, rawModelOutput, structuredOk, factChecksJson, uncertainJson, warningsJson, pendingBody,
—— 标签（1.1.0 / v2 新增）：generatedTagsJson（模型给的标签）, tagsJson（用户最终标签，JSON 字符串数组，不带 #）,
—— 用户文案：finalTitle, finalBody, userEdited, manuallySaved,
—— 元数据：genStatus(NONE/RUNNING/DONE/ERROR/INTERRUPTED), genError, generatedAt, modelName, promptVersion（-1=测试版）, promptSnapshot（完整提示词快照）, note

## links（链接清单，程序独立保存；任务删除时级联删除）
id, taskId, linkId（内部 ID：L1…）, rawUrl（原始字符串，永不覆盖）, resolvedUrl（相对链接解析后的绝对地址）, normalizedUrl（仅用于匹配）,
sourceLocation(input/text/content/page/jina), category(SOURCE 来源/RESOURCE 资源/AUX 辅助/PENDING 待确认), anchorText, name, purpose（AI 建议，只按 linkId 关联）,
userNote, programNote, occurrences, sendToModel, categoryByAi, userConfirmed, verified（程序永不自动置 true）, position

SOURCE（来源）= 用户分享的主链接（帖子、文章、公众号等）以及识别为作者主页 / 个人资料 / 社交帖子的链接。来源链接 sendToModel=false，不发给模型、不进复制和分享的文案，只在应用内展示，标注「来源，不进文案」。主链接本身就是资源时（GitHub 仓库、应用商店、文档，或非内容平台的网站首页）归为 RESOURCE。用户手动改类别会被保留（userConfirmed）。

去重：只有解析后地址完全相同才合并（计次数）；http/https、参数不同都视为不同链接；规范化相同但写法不同只加「可能重复」备注。

## prompt_snapshots
id, version, content, createdAt, label —— 每次保存/恢复默认/导入都生成新版本。

## 迁移（1.0.0 → 1.1.0）
- Room v1→v2（`AppDatabase.MIGRATION_1_2`）：`ALTER TABLE tasks ADD COLUMN generatedTagsJson TEXT NOT NULL DEFAULT '[]'` 和 `tagsJson`（同上），不删除、不改写任何旧数据；没有使用破坏性回退。
- 提示词：`defaultPromptRev` < 2 且当前提示词与 1.0.0 默认逐字相同（忽略首尾空白）→ 保存为新版本的 1.1.0 默认（旧版留在快照）；已自定义则不动。只执行一次。
- 链接：一次性把旧记录中未经用户确认的作者主页 / 帖子链接改为 SOURCE，所有 SOURCE 设 sendToModel=false；旧版主链接若本身是资源（如 GitHub 仓库）改回 RESOURCE。只改类别、发送标记和程序备注，原始 URL 不动。
- 旧记录正文末尾的 #标签 不会自动移动；结果页标签区会出现「移到标签区」按钮，由你决定。

## SharedPreferences
geekradar_settings：baseUrl, model, temperature, jinaEnabled, maxChars, defaultStyle, defaultLength, prompt, promptVersion, promptUpdatedAt, promptInit,
defaultPromptRev, linksReclassV2, coverMode(xhs/generated，默认 generated), clipIntervalMs（默认 1500，范围 300–5000）, defaultTags（空格分隔）, autoOpenXhs（默认 true）
geekradar_secure（加密）：apiKey

## JSON 备份格式
{"format":"geekradar-backup","version":2,"appVersion","exportedAt","tasks":[{…任务字段, "links":[…]}],"prompt":{"content","version"},"promptSnapshots":[…],"settings":{…}}
任务对象新增 "tags"、"generatedTags" 数组；settings 新增 coverMode、clipIntervalMs、defaultTags、autoOpenXhs。version 1（1.0.0 导出）的备份仍可导入，标签为空。
不含 API Key。导入：校验 format/version → 预览（新增/重复数）→ 单事务写入（失败整体回滚）→ 重复 uuid 跳过；提示词和设置只有勾选才覆盖。
CSV / Markdown 导出未实现。

## 封面图
只写入应用缓存 `cache/covers/`（每次生成前清空旧文件），通过 FileProvider（`${applicationId}.fileprovider`）临时授权给接收方读取，不写相册、不申请存储权限。
