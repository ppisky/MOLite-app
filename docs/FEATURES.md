# 功能适配与计划

| 能力 | 当前状态 | core 对应 |
|---|---|---|
| 品牌、用户图标、默认紫色和本机主题持久化 | 已接入 | 应用宿主 |
| 服务商、模型、参数组、三个用途 | 已接入 | provider/model/group.put、purposes.set |
| 流式对话、停止、历史 | 已接入 | chat、history、Task cancellation |
| 多角色选择 | 已接入 | character.put、conversation.put |
| 消息编辑和重生 | 已接入 | exchange.revise / regenerate |
| 记忆、轮次间隔、提前提炼 | 已接入 | memory.list、extraction.*、distill |
| 五维状态/维护状态 | 可展开读取 core 数据 | snapshot、extraction.status |
| 四个提示词空间及恢复默认 | 已接入 | prompt_spaces.* |
| 文件与聊天附件 | 已接入 | file.add / read，chat attachments |
| 加密/无密码 MOC | 已接入 | moc.import / export |
| 安全凭据库 | 优先计划；当前仅进程内密钥 | 需宿主系统存储，不将值写进 core 数据库 |
| 分类、对话注入范围、叙事字段 | 优先计划 | core 已支持，待专用可视化编辑 |
| 冻结请求失败重试与来源跳转 | 优先计划 | core 已支持幂等请求及来源，待交互 |
| 快捷消息、消息收藏 | 有价值，暂留计划，无假按钮 | 需应用端本机状态设计 |
| 记忆五维图与来源时间线 | 有价值，暂留计划 | 使用 core 快照与来源 |
| 图像/视频生成、语音、MCP、Skills、终端、SSH、联网搜索、Web 服务器、云同步、翻译专页 | 已移除 | 当前 core 无对应能力 |

下一阶段依次完善安全凭据库、已有 core 字段的可视化管理、冻结请求重试和来源导航，再评估纯应用端辅助能力。未排具体日期，不把计划当成已支持。
