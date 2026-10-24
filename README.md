# MOLite

带有对话记忆的 Android / Windows 客户端。界面基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 的 Compose Multiplatform 重绘版，业务能力由开源 [MOLite-core](https://github.com/ppisky/MOLite-core) 提供。默认 **MOLite 紫**，也可选择七套原有主题、浅深色模式、AMOLED 或 Android 壁纸动态色；设置保存在本机。

应用图标使用项目用户提供的原图。保留 Material 3 Expressive 的分组卡片、连接按钮、圆角曲面和 HugeIcons。模型参数组及多角色选择使用底部面板。

## 能力与边界

- 真实服务商配置、模型、独立参数组和对话/记忆/视觉回退三种用途。
- core 原生 Chat Completions、Responses、Anthropic Messages；流式生成、停止、历史恢复、消息编辑和重生。
- 多角色对话；对话分别持有 DMW 记忆，支持轮次提炼、提前提炼、维护状态和五维快照查看。
- 四个提示词空间持久保存、恢复内核默认。
- 系统文件选择，文本/图片聊天附件，文件导入与导出，含文件的原生 MOC 导入导出及可选密码。
- 没有 Kotlin 端第二套模型 SDK、数据库或本地 HTTP 服务器，调用链为 Compose → JNI → core C ABI。

**API Key 当前只在本次应用进程保留，重启后需要重新输入。** core 只保存对应环境变量名，密钥不写入 SQLite、MOC 或外观文件。系统安全凭据库列入下一阶段；当前不伪装成已实现。付费供应商和 arm64 真机尚未进行凭据联调。

MCP、Agent Skills、终端/SSH、联网搜索、媒体生成、语音、Web 服务、云同步及翻译专页已从界面移除。快捷消息、消息收藏、记忆图形视图和更完整的分类/注入范围编辑列入 [功能计划](docs/FEATURES.md)，当前未提供这些功能。

PDF 可以存储及随 MOC 携带，暂不解析内容。聊天附件合计最多 16 MiB / 16 个，文件最多 64 MiB；应用系统文件选择单次读取上限 128 MiB。MOC 不含 API Key、应用配色或提示词空间，不等同完整应用备份。旧 Space 对话/记忆树不自动迁移，core 会报告跳过项。

## 构建

当前验证宿主为 Windows x64，支持 Android arm64-v8a/x86_64 和 Windows x64。Android minSdk 26、compileSdk / targetSdk 37。桌面原生构建脚本暂不支持 macOS/Linux，不把这些平台列为已适配。

需要 JDK 25、Python 3、MSVC Build Tools、Rust 1.99.0、Android SDK 37.0 / Build Tools 37.0.0、NDK 28.2.13676358、CMake 3.22.1。Android 通过 NDK 编译 core 的 `cdylib`，生成 `libmolite_core.so` 与薄适配层 `libmolite_jni.so`，使用 16 KiB ELF 页对齐。

把 core 放在相邻目录，或设置 `MOLITE_CORE_DIR`。必须使用 [native/core-version.json](native/core-version.json) 锁定的提交，构建时会检查提交及工作区。源码须干净；`MOLITE_CORE_DEVELOPMENT=1` 仅供本地开发调试，正式构建不要设置。

```powershell
git clone https://github.com/ppisky/MOLite-core ../MOLite-core
$corePin = (Get-Content native/core-version.json | ConvertFrom-Json).revision
git -C ../MOLite-core checkout $corePin
rustup target add --toolchain 1.99.0 aarch64-linux-android x86_64-linux-android

.\gradlew.bat :composeApp:compileKotlinDesktop
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :composeApp:desktopTest
.\gradlew.bat :composeApp:run
# 可运行的 Windows 应用目录，包含运行时和 DLL
.\gradlew.bat :composeApp:createDistributable
```

`local.properties` 不提交，示例：`sdk.dir=D:/Android/Sdk`。脚本优先读取 `ANDROID_HOME` / `ANDROID_SDK_ROOT`、`ANDROID_NDK_HOME`、`JAVA_HOME`。受限网络可设置本地 `google.maven.url`、`central.maven.url` 镜像，默认使用官方仓库。

Android APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。原生库：`build/native/android/<abi>/`。Windows 目录：`composeApp/build/compose/binaries/main/app/MOLite/`。调试 APK 使用开发签名，不是正式签名发行版。

## 开始使用

设置 → 服务商 → 模型 → 模型参数组 → 模型用途。服务商填写 API 前缀，**不包含** `/chat/completions` 等完整请求路径；协议在参数组选择。所有参数与引用由 core 再次校验。新建对话后可从底部面板选择一个或多个角色。

模型参数填写所选协议的原生 JSON，例如 Responses 使用 `max_output_tokens`，Anthropic 使用 `max_tokens`。MOLite 不会把不兼容参数默默改写。视觉回退需使用视觉模型。

## 验证

桌面测试通过真实 JNI / DLL 调用，使用本机模拟供应商，不需要 API Key。覆盖流式与 Unicode、停止和句柄释放、冻结请求幂等、重启持久化、提示词和加密 MOC 往返；另有支持页面、窄屏导航、主题和底部面板截图测试。

Android 设备验证使用独立包 `io.github.ppisky.molite`，不会清除原版 RikkaHub 或旧预览版数据：

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
adb -s 127.0.0.1:16448 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s 127.0.0.1:16448 install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s 127.0.0.1:16448 shell am instrument -w io.github.ppisky.molite.test/me.rerere.rikkahub.preview.CoreSmokeInstrumentation
python tools/capture_adb.py --serial 127.0.0.1:16448 --package io.github.ppisky.molite --out composeApp/build/screenshots/android-molite-chat.png
```

设备测试实际加载 APK 中的 `.so`，用设备内本地 HTTP 模拟供应商检查流式、Unicode 和持久化。可选 `am instrument -w -e verify_https true -e https_url https://www.baidu.com ...` 进行无凭据、非模型接口的证书验证检查，以收到 HTTP 错误确认 TLS 已通过；不指定 URL 时使用 example.com，需设备网络可达。验证范围和局限见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。未宣称整应用像素一致。

## 许可、协议与源码

**应用：AGPL-3.0；MOLite-core：Apache-2.0。** 动态链接不抹去上游许可。完整文本、来源、修改和第三方许可见 [LICENSE](LICENSE)、[NOTICE](NOTICE)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [licenses/](licenses/)。应用“关于 → 离线开源许可”提供主要文本。

C ABI v1 的 UTF-8、事件、取消与资源释放合同，以及 Android TLS 初始化、供应商请求协议和版本策略见 [docs/CORE_INTEGRATION.md](docs/CORE_INTEGRATION.md)。本仓库保留 RikkaHub 上游 Git 历史及署名。
