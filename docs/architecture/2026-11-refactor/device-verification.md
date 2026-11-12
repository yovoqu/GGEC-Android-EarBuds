# 真机验证记录（2026-11-12）

> 目标：把「扫描设备」这条链路在真机上跑通并留证；连接/控制链路本轮**预留**（代码与契约已就绪，未做真机连接验收）。
> 设备：motorola edge 60 pro（`NEPI4E0106`，Android 15 / SDK 35，arm64-v8a）。

## 1. 验证范围

| 链路 | 状态 | 证据 |
|------|------|------|
| 应用启动 → 设备页空态 | ✅ 通过 | `01-launch.png` |
| 进入扫描页、自动开扫 | ✅ 通过 | `02-scan-or-permission.png`、日志 `YoBleTimeline: scan-state STARTING → SCANNING, gen=1` |
| BLE 扫描 → 附近耳机识别（机型/名称/地址/RSSI） | ✅ 通过 | `02/09-scan-again.png`（7 台，HS01 识别 + 未登记机型原样展示） |
| 机型筛选选项来自 biz 目录 | ✅ 通过 | `12-filter-tap.png`（`全部设备 / HS01 / HS02`） |
| 筛选生效 + 空态文案 | ✅ 通过 | `13-filter-hs02.png`（"当前筛选下暂无设备…切换右上角设备类型可查看全部结果"） |
| 筛选复原、列表恢复、RSSI 实时增量更新 | ✅ 通过 | `14-scan-stopped.png`、`15-stop-retry.png`（RSSI 值逐帧变化） |
| 停止/开始搜索按钮点击与状态翻转 | ✅ 通过 | 仪器化 `ScanScreenFlowTest#scanPageTogglesBetweenStopAndStart` |
| 设备目录在真机的契约正确性 | ✅ 通过 | 仪器化 `DeviceStackSmokeTest#catalogExposesHsModelsOnDevice` |
| 真实 BLE 扫描返回设备 | ✅ 通过 | 仪器化 `DeviceStackSmokeTest#nearbyScanFindsHsDevicesOnDevice` |
| 未连接写入拒绝为 `NOT_READY` | ✅ 通过 | 仪器化 `DeviceStackSmokeTest#writeOnIdleSessionIsRejectedAsNotReady` |
| 设置页六个能力分组可打开并渲染 | ✅ 通过 | 仪器化 `DeviceSettingsScreenTest` + `08-settings-*.png` |
| **连接 + 控制写入（真实耳机往返）** | ⏸ **预留** | 已有 `connectReachesTerminalState` 覆盖到"进入终态 + 只读能力拒绝"，未做完整业务往返验收 |

## 2. 仪器化结果（真机实跑）

```
adb shell am instrument -w -r \
  -e class com.ggec.earbuds.device.DeviceStackSmokeTest,\
com.ggec.earbuds.device.ScanScreenFlowTest,\
com.ggec.earbuds.device.DeviceSettingsScreenTest \
  com.ggec.earbuds.test/androidx.test.runner.AndroidJUnitRunner
→ OK (6 tests)
```

前提（真机环境要求，已在本次执行）：`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` 已授予；系统动画三项置 0（Espresso 要求）。

## 3. 扫描链路读到的运行证据

- 应用进程日志：`[ble-scan] scan-state STARTING → SCANNING`、`scan started gen=1, filters=0, timeoutMs=0`，停止后 `scan stopped gen=1 → IDLE`。
- 扫描结果识别出 `HS01` 机型（来自 biz 设备目录），同时把目录外设备以广播名 + 地址展示（`未登记机型` 路径），对应设计 §5「目录未登记机型仍可连接、能力集合为空」。
- 筛选下拉的两项 `HS01/HS02` 与 biz `HsCatalogImpl` 登记的机型一致；筛选后无匹配时走"当前筛选下暂无设备"的专用文案，而不是通用空态。
- 列表 RSSI 逐帧刷新走 DiffUtil payload 轻量绑定（截图间数值变化、名称未重排）。

## 4. 本轮顺带清掉的两项既有债务

| 项 | 根因 | 处理 | 结果 |
|----|------|------|------|
| `lib_common` 单测整包编译失败（基线 HEAD 可复现） | `compileSdk 37` 的 `android.jar` 已无 `android.hardware.fingerprint.FingerprintManager`，而 `org.robolectric.Shadows` 类含该类型重载，用例调用 `Shadows.shadowOf(...)` 时 javac 加载失败 | 3 处调用改 `Shadow.extract(...)`（语义等价）；新增 `lib_common/src/test/resources/robolectric.properties` 钉 `sdk=35`（Robolectric 4.14.1 支持上限，模块 targetSdk 为 37） | `lib_common` 单测 **171 用例全绿**（此前从未能编译） |
| `lib_ai/ResponsesTest` 收尾吞 `IOException` | 测试用 try-with-resources 关闭 `MockWebServer`，服务端 WebSocket 不应答关闭帧导致队列收尾超时 | 服务端 `WebSocketListener` 增 `onClosing → close(1000)`；`shutdown()` 不再 catch；去掉 `Thread.sleep(500)` | `lib_ai` **411 用例全绿**，收尾失败可见 |

## 5. 全量回归（本机实跑）

```
.\gradlew.bat test :app:assembleDebug :biz_ggec_hs:check :lib_bluetooth:check --offline --console=plain
→ BUILD SUCCESSFUL
```

| 模块 | 用例 | 失败 | 跳过 |
|------|------|------|------|
| app | 540 | 0 | 0 |
| biz_ggec_hs | 129 | 0 | 0 |
| lib_bluetooth | 176 | 0 | 0 |
| lib_common | 171 | 0 | 0 |
| lib_ai | 411 | 0 | 0 |
| biz_appupdate | 58 | 0 | 0 |
| yotheme | 1 | 0 | 0 |
| **合计** | **1486** | **0** | **0** |
| 真机仪器化 | 6 | 0 | 0 |

## 6. 真机环境的两个注意点（不属产品缺陷）

1. 该机器上并行运行了其它应用（含 `com.yohu.youidemo`、短视频类应用），会抢占前台与输入焦点：`adb shell input tap` 注入会被吞，`uiautomator dump` 也会抓到别的窗口。因此 UI 断言统一走 **Espresso（直接操作本应用视图树）**，实机截图用于呈现证据。
2. `YoSelect` 内部 TextView 在 Espresso 的 90% 可见性判定下点击不稳定（几何换算），筛选项不做 Espresso 点击断言，改由真机截图 + 真实目录契约测试覆盖。
