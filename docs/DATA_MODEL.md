# 数据结构说明（Room，数据库 geekradar.db，version 1）

## tasks（一次任务）
id, uuid（备份去重用）, createdAt, updatedAt, inputType(url/text/share), rawInput（完整原始输入，永不丢弃）,
originalUrl（主来源原样字符串）, finalPageUrl, pageTitle, pageDescription, sourceText（抓取或粘贴的原文）, sourceEdited,
fetchStatus(NONE/OK/PARTIAL/FAILED/MANUAL), fetchChannel(local/jina/manual/none), fetchError, fetchedAt, style, length,
—— AI 结果（与原文分离）：titleCandidatesJson, selectedTitle, generatedBody, rawModelOutput, structuredOk, factChecksJson, uncertainJson, warningsJson, pendingBody,
—— 用户文案：finalTitle, finalBody, userEdited, manuallySaved,
—— 元数据：genStatus(NONE/RUNNING/DONE/ERROR/INTERRUPTED), genError, generatedAt, modelName, promptVersion（-1=测试版）, promptSnapshot（完整提示词快照）, note

## links（链接清单，程序独立保存；任务删除时级联删除）
id, taskId, linkId（内部 ID：L1…）, rawUrl（原始字符串，永不覆盖）, resolvedUrl（相对链接解析后的绝对地址）, normalizedUrl（仅用于匹配）,
sourceLocation(input/text/content/page/jina), category(SOURCE/RESOURCE/AUX/PENDING), anchorText, name, purpose（AI 建议，只按 linkId 关联）,
userNote, programNote, occurrences, sendToModel, categoryByAi, userConfirmed, verified（程序永不自动置 true）, position

去重：只有解析后地址完全相同才合并（计次数）；http/https、参数不同都视为不同链接；规范化相同但写法不同只加「可能重复」备注。

## prompt_snapshots
id, version, content, createdAt, label —— 每次保存/恢复默认/导入都生成新版本。

## SharedPreferences
geekradar_settings：baseUrl, model, temperature, jinaEnabled, maxChars, defaultStyle, defaultLength, prompt, promptVersion, promptUpdatedAt
geekradar_secure（加密）：apiKey

## JSON 备份格式
{"format":"geekradar-backup","version":1,"appVersion","exportedAt","tasks":[{…任务字段, "links":[…]}],"prompt":{"content","version"},"promptSnapshots":[…],"settings":{…}}
不含 API Key。导入：校验 format/version → 预览（新增/重复数）→ 单事务写入（失败整体回滚）→ 重复 uuid 跳过；提示词和设置只有勾选才覆盖。
CSV / Markdown 导出未实现。
