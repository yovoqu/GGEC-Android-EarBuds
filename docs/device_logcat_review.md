# 实机 Logcat 全量审查报告（lib_bluetooth / lib_ai）

> 日期：2026-11-10
> 设备：motorola edge 60 pro（serial `NEPI4E0106`），Android 15 / API 35 / arm64-v8a，`ro.debuggable=0`
> 被测：`com.ggec.earbuds`（versionName 1.0，DEBUGGABLE），本次重装 APK `app-debug.apk`（sha256 `df08b497…42bfb2c`，16:54 构建，含 lib_bluetooth + lib_ai 全部重构）
> 目标：使用实机 Logcat 实测审查 lib_bluetooth / lib_ai 两个模块

---

## 1. 实测步骤

1. `gradlew :app:assembleDebug` → BUILD SUCCESSFUL；`adb install -r` 覆盖安装（replaced=true，保留数据）。
2. 冷启动 `am start -n com.ggec.earbuds/.ui.shell.MainActivity`，`logcat -c` 后采样。
3. 进入“设备 → 添加设备”，触发 BLE 扫描 15s。
4. 进入“AI → 语音交流模式 → 语音通话”，与已配置的 Qwen 实时语音模型对话。
5. 采集中途 `logcat -d`（按 pid 与全量两种），并检查 `-b crash`。

## 2. 结果证据

### 2.1 冷启动（全量 logcat 1222 行）
- lib_bluetooth 标签命中 **8 行**，示例：
  - `I YoCbtAndroidClient: YoCbtAndroidClient 已创建，SDK=35`
  - `D YoBleReceiverRegistry: Registered receiver: YoBleAdapterStateReceiver / YoBleBondStateReceiver`
  - `D YoCbtAndroidReceiverRegistry: 接收器已注册: ...YoCbtDiscoveryReceiverAdapter / BondState / Acl / Sdp / Profile...`
- `FATAL EXCEPTION / ANR / Process: com.ggec.earbuds` = **0**。
- 结论：lib_bluetooth 的 `YoBtLogger → YoLog → logcat` 链路在实机生效，启动无崩溃。

### 2.2 BLE 扫描（进入“添加设备”）
- `D YoBleTimeline: [ble-scan] scan started gen=1, filters=0, timeoutMs=15000, ...`
- `D YoBleTimeline: [ble-scan] scan stopped gen=1`
- 界面实测：发现 **7 台附近耳机**（Vidda V12D0 / V12耳夹式若干 / Hisense HiClips，RSSI -71~-88 dBm）。截图：`.rea-captures/03_scan/screen.png`
- 结论：BLE 扫描功能与日志正常，无崩溃。

### 2.3 AI 实时语音通话
- 实测：Qwen 实时语音通话成功建立并对话（界面显示“正在思考 00:22”，AI 正常回复）。截图：`.rea-captures/07_voice/screen.png`
- 系统侧日志：`AudioRecord ... start(866): return status 0`（采集正常）；`-b crash` = **0**。
- 结论：lib_ai realtime 链路在实机可正常跑通，无崩溃。

## 3. 审查发现（关键）

1. **lib_bluetooth 日志链路实机验证通过**（启动 + BLE 扫描均有结构化日志、无崩溃）。
2. **lib_ai 新增日志未在实机出现（0 行 YoAi 标签）**，原因明确：
   - App **未调用 `YoLog.init(...)`**（全仓库 app 源码无 `YoLog.init`），YoLog 走“未初始化→logcat 兜底”通道；因此无落盘文件日志，只能靠 logcat。这本身是需要补的集成点。
   - 实测的 AI 路径是**实时语音（realtime）**：`app → com.yovo.lib_ai.realtime.YoRealtime`（由 `RealtimeLinkAdapter` 直接使用），**不经过 `YoAiAssembly`/`OkHttpTransport`/`ProtocolSupport`**（这些已接入日志），且 realtime 类位于**公开包**、当前不能直接 import internal `YoAiLogger`，故无日志。
   - 文本对话平台未配置（AI 设置文本模式显示“还没有接入文本模型”），因此 `YoAiAssembly.open()` 未被触发（`YoAiClien` 未装配）。

## 4. 处置建议（下一轮）

1. App `Application` 中初始化 YoLog（`YoLog.init(context, LogConfig…)`），开启文件落盘，便于实机全量日志留存与追溯。
2. 为 **public 包日志**加一个公开桥（类似现有 `YoAiDiagnostics.Sink`，扩展 `log(level,tag,msg)`），使 `realtime`/`speech` 等公开包能经 `Sink` 落到 internal `YoAiLogger`，避免 public→internal 越界。
3. 接入 **realtime** 的连接建立/关闭/错误日志（`RealtimeWebSocketLink` / `YoRealtime`）。
4. 配置文本模型（Qwen/DeepSeek 任一）后，跑一次文本对话，即可在 logcat 看到 `YoAiAssembly: opening YoAiClient…` 与 `OkHttpTransport` / `YoAiDiagnosticsBus` 记录。

## 5. 证据文件

- 冷启动全量日志：`.rea-captures/logs/full_startup.txt`（1222 行）
- 扫描期日志：`.rea-captures/logs/app_scan2.txt`
- 通话期日志：`.rea-captures/logs/app_call_live.txt`
- 崩溃缓冲：`.rea-captures/logs/crash.txt`（0 行）
- 截图：`.rea-captures/03_scan/screen.png`、`.rea-captures/07_voice/screen.png`

## 6. 设备状态副作用（说明）

本次实测对设备做了：覆盖安装 debug 包、`pm grant RECORD_AUDIO`、进入过 AI 设置并切换过文本/语音模式、发起并挂断一次语音通话。均在测试范围内；如需还原 AI 设置请手动调整。
