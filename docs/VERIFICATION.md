# 验证记录

日期：2026-10-24（Asia/Shanghai）。精确 core 提交见 native/core-version.json。

## 本地构建与测试

- Windows x64，JDK 25，Rust 1.99.0，Android SDK 37.0 / Build Tools 37.0.0，NDK 28.2.13676358，CMake 3.22.1。
- `:composeApp:compileKotlinDesktop`、`:androidApp:assembleDebug`、`:composeApp:desktopTest`。
- `:androidApp:assembleDebugAndroidTest`、`:composeApp:createDistributable`。
- 6 个桌面测试：2 个真实 JNI/core 联调、4 个页面/窄屏/导航/主题/底部面板测试。真实内核测试使用本地模拟供应商，验证流式、中文/emoji、进程内 API Key 到鉴权头的传递及关闭后的清除、停止释放、历史恢复、冻结请求幂等、提示词持久化、加密 MOC 往返及失败任务释放。
- core 自身 `cargo fmt --check`、`cargo test --locked`、`cargo clippy --locked --all-targets -- -D warnings` 通过。Android 两种架构实际编译，Android 专用初始化函数参与编译。
- Windows 随包 JVM、Compose 类、DLL 和 C ABI 通过 `python tools/test_distribution.py` 检查。

## Android 实际设备检查

- MuMu，ADB serial `127.0.0.1:16448`，Android x86_64，1080×1920，480 dpi。
- 本次应用 MOLite 0.1.0，包名 `io.github.ppisky.molite`。沿用重绘版布局的上游参考 APK 为 RikkaHub 3.1.0，但本轮是 MOLite 功能和品牌检查，没有重新声明像素匹配。
- 未清除原版 `me.rerere.rikkahub` 或旧预览包数据。
- 实际 APK 安装启动，主界面截图通过 tools/capture_adb.py 校验前台包。截图为未缩放、未裁剪的完整 1080×1920，路径 `composeApp/build/screenshots/android-molite-chat.png`（构建目录不提交）。
- APK 含 arm64-v8a/x86_64 的 core 和 JNI `.so`，以及对应 AndroidX 原生图形库；NDK 构建设置 16 KiB ELF 对齐。
- `CoreSmokeInstrumentation` 实际加载 APK 内的库，通过设备内本地 HTTP 模拟供应商验证流式、Unicode、历史及关闭重开后持久化，不使用凭据或付费模型。
- HTTPS 附加检查使用 `-e verify_https true -e https_url https://www.baidu.com`：先由系统 HttpsURLConnection 验证同设备连通性，再由 core 请求不存在的 completion 路径并收到 HTTP 错误，确认系统证书验证链通过。example.com 在该设备网络下连接失败，未把该失败当作通过。

## 实际范围与局限

已检查品牌、默认主题、主界面以及桌面自动生成的设置、主题、服务商、角色、关于和窄屏截图。仅对本次实际显示的页面作结论，未进行整应用像素评分，也没有将早期 UI 局部差异分数套用于 MOLite。

没有付费供应商真实凭据调用、arm64 真机运行、生产签名或商店审核。所有“已接入”表示界面调用当前 core 操作，不意味着所有协议和供应商已经实网覆盖。API Key 当前仅进程内；安全凭据库和其他未完成能力在 FEATURES.md 中明确列出。
