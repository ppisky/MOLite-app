# MOLite / core 接口合同

## 版本与部署

精确 source revision、core 版本、ABI 及许可在 `native/core-version.json` 中锁定。构建从干净的相邻 core 源码编译，禁止把不明版本 DLL/SO 当成已验证 core。Android arm64-v8a、x86_64：`libmolite_core.so` + `libmolite_jni.so`；Windows x64：`molite_core.dll` + `molite_jni.dll`。Android 库按 16 KiB 页对齐，未启用 UPX。

Compose → `CoreClient` → `CoreBridge` → JNI → `include/molite.h`，不启动 `molite-server`。数据库、文件、网络、DMW 和 MOC 均由 core 负责。Kotlin 宿主只处理生命周期、UI、文档选择、外观与传输临时文件。

## C ABI v1

- `molite_abi_version()` 必须返回 1，不匹配直接报错。
- `molite_open(path_bytes, length, &engine)`：路径为真实 UTF-8，不是 Java modified UTF-8 或 NUL 终止字符串；主线程外打开。
- Engine / Task 为 `uint64_t` 不透明句柄，JNI 使用 `jlong` 承载原始位，不解引用。
- `molite_submit(engine, json_bytes, length, &task)`：UTF-8 JSON，显式 `version:1`，与 core HTTP 操作合同相同，应用不调用 HTTP 宿主。
- `molite_task_next(task, 100, &buffer)`：在 IO 调度器轮询，每次最长 100ms。状态 0 为事件，1 为 EMPTY，其他状态报错。
- 每个成功输出的 `{data,len}` 无 NUL。JNI 复制到 Java byte[] 后，恰好调用一次 `molite_buffer_free`，即使 Java 分配失败也释放。
- 事件 `text.delta` 用于增量显示；`completed.result` 为业务结果，`failed.error` 为可见错误，`cancelled` 为终止。其他事件保留为扩展，不伪造成功。
- `finally` 在不可取消段请求停止并释放 Task。所有任务释放后关闭 Engine。状态 0 OK、1 EMPTY、2 INVALID、3 BUSY、4 FAILED。
- UI 操作串行，网络和轮询不阻塞主线程。宿主任务上限 900 秒，core 参数组超时 1–600 秒。
- 取消不保证回滚已经提交的交换，停止后重新读取历史；失败不伪造回复，不把未提交的增量写为历史。
- 新生成使用新 UUID。冻结请求重试须复用原 request_id 和输入；core 幂等行为已有原生联调测试，完整重试交互列入计划。

## Android HTTPS

先在 JVM 线程调用 `molite_android_init(JNIEnv*, ApplicationContext)`，再打开 Engine。core 使用 `rustls-platform-verifier` 的系统证书链校验；宿主必须包含与 core Cargo.lock 一致的 `org.rustls:rustls-platform-verifier:0.2.0` AAR。JNI 不保存 Activity。这个 Android 专用初始化扩展不改变 ABI v1 既有任务布局。其他平台不导出它。

Android 只申请 INTERNET。系统文档选择使用 SAF，不申请整个存储目录、相机或麦克风权限。应用包 ID `io.github.ppisky.molite`，与原版/旧预览版独立。

## 供应商协议与参数

服务商保存 base_url 前缀、支持的协议和密钥环境变量名。模型保存 provider_id、接口模型 ID、vision。参数组独立保存 model_id、protocol、stream、timeout_seconds 和原生 parameters。

| 协议 ID | core 拼接路径 | 凭据 |
|---|---|---|
| chat_completions | /chat/completions | Authorization: Bearer |
| responses | /responses | Authorization: Bearer |
| anthropic | /messages | x-api-key + anthropic-version |

三个用途：conversation、memory、vision_fallback。视觉回退可空，其余使用实际参数组；参数名不互相转换，不允许 parameters 覆盖 model/messages/input/system/stream/tools 等控制字段。具体合同以锁定版本 core 的 docs/api.zh-CN.md 为准。

## 数据与凭据

Android 数据在应用私有 files/molite，Windows 在 `%LOCALAPPDATA%/MOLite`。外观只保存主题、动态色、浅深色、AMOLED 和最后对话 ID。API Key 仅通过 JNI 设置进程内 `MOLITE_APP_KEY_*`，不存入 preferences、数据库和 MOC；重启需重输。JNI 的临时 byte[]/C++ 缓冲会清零，但 JVM 字符串不提供可验证的全量清除保证。系统安全凭据库尚未实现。

Android ViewModel 保持 Engine 生命周期；桌面关窗先停止/释放任务再关闭。导入导出通过系统选择器，必要时复制为私有 transfer-UUID.moc，finally 清理，不覆盖 core 的已有导出路径。MOC 不包含提示词空间与主题，也不声称是完整应用备份。

## 开源许可

UI 衍生代码与应用组合分发遵循 AGPL-3.0，core 独立源码为 Apache-2.0。保留两者版权、完整许可、NOTICE 和相应源码；不因 `.so` 动态链接把应用宣称为 Apache-only。详情见根目录 THIRD_PARTY_NOTICES.md。
