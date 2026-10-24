MOLite 首个 core 集成预览版：默认可配置紫色主题，使用用户提供的图标；Android 内嵌 arm64/x64 `.so`，Windows 包含 JVM 和对应 DLL。

已接入服务商、模型参数组、三种模型用途、流式对话、停止、多角色、消息编辑/重生、DMW 记忆、提示词空间、文件附件及加密 MOC。core 不支持的服务入口已删除，后续能力见应用内“功能计划”。

应用采用 AGPL-3.0，core 独立采用 Apache-2.0。源码保留上游署名、NOTICE、精确 core 提交和第三方许可证，应用内提供主要离线文本。

验证：桌面编译、Android 构建、6 个桌面测试、MuMu 上实际 `.so` 的流式/持久化/系统 HTTPS 验证，以及 Windows 随包运行时/DLL 检查通过。没有使用付费供应商凭据，arm64 已编译但未在 arm64 真机运行。

API Key 当前仅保留在本次进程，重启后需重新输入。MOC 不包含密钥、应用配色和提示词空间。Android APK 使用开发签名；Windows ZIP 解压后运行 MOLite.exe，均为预览产物。
