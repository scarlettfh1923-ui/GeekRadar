# 极客雷达 GeekRadar 1.0.0

一键把科技工具 / AI 应用 / GitHub 仓库 / 文章链接转成小红书中文文案的 Android 应用（Kotlin + Jetpack Compose + Room）。包名 `com.scarlett.geekradar`，minSdk 26，targetSdk 35。

## 功能
- 三种输入：系统分享（SEND / PROCESS_TEXT，应用在后台也能接收）、粘贴链接、粘贴原文。识别到多个链接时让你选主链接，其余全部保存。
- 抓取：OkHttp + Jsoup 提取标题、描述、正文和全部超链接，相对链接会解析成绝对地址，同时保留原始写法。本机失败或正文少于 200 字时，自动改用 Jina Reader 再读一次（默认开启，可关闭）；还不行就粘贴原文继续。
- 链接保护：调用模型之前，程序先给每条链接编号（L1…）并按类别保存（原始来源/资源/辅助/待确认）。模型只能通过编号给出名称、用途和类别建议；正文里的 [L2] 在复制时由程序换回原始 URL；模型文案中出现的陌生 URL 或不存在的编号会显示警告。
- DeepSeek：Base URL、模型名、Key 都可配置（Key 存在 Keystore 加密存储里），支持连接测试，错误会分类提示，输出解析有容错。
- 提示词：可编辑、保存时自动生成版本快照、可恢复默认、可用未保存的版本测试；每条记录都保存当时用的提示词快照。
- 结果页：3 个标题候选、标题和正文完整可编辑、自动保存草稿、手动保存、重新生成前确认（覆盖或保留修改）、复制完整文案/仅正文/全部链接、事实核查和不确定信息提示。
- 历史：按标题/URL/正文搜索、按日期分组、继续编辑、重新生成、复制、删除（删除和清空都要确认）。
- 备份：JSON 导出和导入（不含 API Key，导入前预览，重复记录跳过，整体事务写入）。

## 目录
- `app/src/main/java/com/scarlett/geekradar/core`：URL、抓取、链接分类、提示词、模型客户端、解析、组合（不依赖 Android，有单元测试）
- `data`：Room、设置、加密 Key、备份、业务编排　`ui`：Compose 界面（方案 C）
- `tools/mock_server.py`：开发用的 mock 接口　`docs/`：文档

## 文档
[BUILD.md](docs/BUILD.md) · [API_CONFIG.md](docs/API_CONFIG.md) · [default_prompt.md](docs/default_prompt.md) · [DATA_MODEL.md](docs/DATA_MODEL.md) · [TESTING.md](docs/TESTING.md) · [LIMITATIONS.md](docs/LIMITATIONS.md)
