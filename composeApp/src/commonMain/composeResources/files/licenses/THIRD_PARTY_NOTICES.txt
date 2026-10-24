# MOLite 开源许可与第三方声明

## 应用与上游

MOLite 应用是 RikkaHub 的衍生作品，应用源代码和组合分发遵循 GNU Affero General Public License v3.0（AGPL-3.0），完整文本见根目录 LICENSE。Copyright © RikkaHub contributors；MOLite 的新增改动由 MOLite contributors 提供。保留上游历史、版权、免责声明及源代码获取途径。修改后分发须遵守 AGPL 的相应源代码等要求；通过网络与用户交互的修改版本还须遵守该许可证第 13 条。

- 应用源码：https://github.com/ppisky/MOLite-app
- 上游：https://github.com/rikkahub/rikkahub
- UI 基准版本：db49822836524ac498cb6a3cb1d8fbd0e7815fa0
- 使用的主题、卡片、连接按钮等来源见 NOTICE。

## MOLite-core

core 独立采用 Apache License 2.0，不因为应用使用 AGPL 而把其独立源码改为 AGPL。应用通过 C ABI 和 JNI 动态链接；不能将整个应用标成只有 Apache-2.0。二者组合分发遵循应用 AGPL 条款，并保留 core 的 Apache-2.0 许可、版权、NOTICE、第三方声明及修改说明。

- 源码：https://github.com/ppisky/MOLite-core
- 精确版本：native/core-version.json（含 Git revision、版本与 ABI）
- 完整许可：licenses/MOLite-core-Apache-2.0.txt
- 来源声明：licenses/MOLite-core-NOTICE.txt（包括 Apache-2.0 的 MOMO Core MOC/加密复用）
- 本次适配在 core 增加 Android 专用 `molite_android_init`，用于系统 HTTPS 证书验证。
- 依赖清单与原始许可：licenses/RUST-DEPENDENCIES.md 和 licenses/rust/。这是 Cargo 解析出的依赖全集，含开发及其他平台依赖，不代表每项都打包进 Android。

## UI、运行时、图标与字体

| 部分 | 来源 | 许可与说明 |
|---|---|---|
| Kotlin、kotlinx.coroutines、kotlinx.serialization | JetBrains | Apache-2.0 |
| Compose Multiplatform、Material 3、AndroidX Activity/Lifecycle | JetBrains / Android Open Source Project | Apache-2.0；各发行包保留自己的元数据与版权 |
| Skiko / Skia（桌面） | JetBrains / Google | Apache-2.0 / BSD-3-Clause；发行物附带相应原始声明 |
| Rustls Android verifier AAR 0.2.0 | rustls/rustls-platform-verifier | MIT OR Apache-2.0；选择 Apache-2.0，版本须与 core Cargo.lock 对齐 |
| HugeIcons Stroke Rounded 图形 | Hugeicons / rikkahub/hugeicons-compose | 原始免费图形为 MIT；保留图形版权。Compose 路径的具体来源版本见 NOTICE，未擅自为独立封装仓库推断其未声明的许可 |
| JetBrains Mono | JetBrains s.r.o. | SIL Open Font License 1.1，见 FONT-LICENSE.txt |
| MOLite 图标 | 本项目用户提供 | 按用户要求用于此项目，未声称是 RikkaHub 或 Hugeicons 图标；第三方使用仍须尊重原始权利 |

免费 Hugeicons 的官方许可说明：https://hugeicons.com/docs/faqs/license-questions 。Apache 与 GPLv3 的兼容性说明：https://apache.org/licenses/GPL-compatibility.html 。组合许可参考：https://www.gnu.org/licenses/license-compatibility.en.html 。这些链接是解释资料，许可证完整文本及实际分发义务以原文为准。

应用“关于 → 离线开源许可”可阅读 AGPL-3.0、Apache-2.0、OFL-1.1 和本声明。发布源码同时提供 licenses/ 和精确 core revision；不包含 API Key、用户数据或私有构建路径。
