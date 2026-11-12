# 设备模块治理 · 最终验收与残余风险报告

> 日期：2026-11-10
> 目标（Goal）：全量审查 GGEC-Android-EarBuds 设备模块，自底向上梳理 蓝牙层(BLE/BT)→协议层→应用层 的完整架构与数据链路；严格 MVVM、杜绝 CBT 与 BLE 耦合；修复扫描卡死/扫描类型无法切换等应用层 Bug；清理冗余补丁与防御性代码；每个问题与修改保留完整证据链。
> 交付主线：`docs/device/round1_device_module_audit.md`（§1–§17）；分层子报告 `docs/audit/round1_{ble,classic,protocol}.md`；真机 `docs/device/round2_device_verification.md`。

---

## 1. 目标达成矩阵

| 目标项 | 状态 | 证据 |
|---|---|---|
| 自底向上梳理三层架构与数据链路 | ✅ | 总报告 §1 模块依赖图、§2 蓝牙层、§3 协议层、§4 应用层；§10 连接链路最终形态 |
| 蓝牙层 BLE/CBT 严禁耦合 | ✅ | ble/classic 审计报告「解耦结论」：双向零 import、独立 Runtime/执行器；应用层 `HsDevicePort` 移除 `YoCbtClient`（§6.1） |
| 修复扫描设备卡死（应用层 Bug） | ✅ | 应用层：主线程已配对查询移出 + 扫描启动异常闩锁复位（§6）；蓝牙层：BLE-01/02/04/05 + **BLE-03 平台 binder 异步化**（§12/§18）；真机扫描/停止/重启正常（§18.4） |
| 修复扫描设备类型无法切换 | ✅ | MVVM 过滤契约解耦 + 选项单一来源（§16）；真机「全部/HS01/HS02」切换即时生效（round2/§16.4、round6_final_hs02） |
| 严格 MVVM | ✅ | `ScanViewModel` 去 `R`/去线程（§6.2/§11.2）；`checkAppMvvmLayers` 等 10 项 Gradle 校验通过 |
| 清理冗余补丁/防御性代码 | ✅ | 第一轮：`CommandQueryGroup` 整类 + 多组无调用 API（§11）；第二轮：CommandScheduler/DeviceEventPipeline/AdvertiseStatus 死方法（§17） |
| 每个问题/修改留证据链 | ✅ | 每节均含 `文件:行`、调用链与验证命令/结果 |

**结论：目标已达成。** 残余项均为「理论风险 / 需特定硬件场景验证」的 P2 级后续优化（见 §4），不阻塞目标。

---

## 2. 关键变更汇总（跨轮）
1. **应用层扫描健壮性（§6）**：`HsDevicePort` 扫描启动移出主线程、BLE-only 已配对、异常复位 `scanning` 闩锁；`ScanViewModel` 去 `R`（失败事件改领域枚举 `DevicePort.ScanFailure`，资源映射下沉 `ScanActivity`）；扫描 API 收敛为单一 `startScanBatch`（§13）。
2. **连接数据链路打通（§10）**：协议层新增 `HsConnectionObserver` + `YoHsClient.setConnectionObserver`（H-01）；`HsDevicePort.connect` 接入 `YoHsSdk/YoHsClient`，`CONNECTING→READY/DISCONNECTED` 驱动 `DeviceSnapshot`；首页 `CONNECTING` 可见 + 12s 就绪超时（§16 前）；真机验证 GATT 连接/服务发现/MTU/通知使能。
3. **协议层 H-02（§10.1）**：`HsProtocolEngine.submit` 将 `Map` 结果落地 `DeviceEventPipeline.applyAggregatedHomeStatus`，首页/全局状态查询进入域存储。
4. **蓝牙层扫描 P1（§12）**：`YoBleScanController` 修 BLE-01/02/04/05；能力检查器抽接口（`YoBleCapabilityInspector` + `YoBleAndroidCapabilityInspector`）以便测试；新增 `YoBleScanControllerTest`。
5. **协议层入站拼帧（§14，H-03）**：新增 `HsInboundFrameAssembler`，分片重组/粘包拆分/组合电量固定长/1 字节重同步；`HsProtocolEngine.onInbound` 接线。
6. **协议层长帧分片写（§15，H-04）**：`YoBleCommandLink.write` 按 MTU 动态选择单写 / `writeSplit`。
7. **MVVM 收尾（§11.2/§16）**：调度器下沉 `ScanPublishScheduler`；过滤契约与选项单一来源解耦。
8. **冗余清理（§11/§17）**：删除 `CommandQueryGroup` 整类及多组无调用 API/方法。

---

## 3. 验证证据（本轮最终回归）

| 项 | 命令 | 结果 |
|---|---|---|
| lib 边界 4 项 | `checkLibBluetooth{ProviderBoundary,ApiSurface,LogSeam}` + `checkNoStrayLogging` | PASS |
| biz 边界 | `:biz_ggec_hs:checkBizGgecHsBoundary` | PASS |
| lib 单测 | `:lib_bluetooth:testDebugUnitTest` | 26 套件 / 105 用例 / 0 失败 |
| biz 单测 | `:biz_ggec_hs:testDebugUnitTest` | 4 套件 / 17 用例 / 0 失败 |
| app 单测 | `:app:testDebugUnitTest` | 56 套件 / 421 用例 / 0 失败 |
| 打包 | `:app:assembleDebug` | BUILD SUCCESSFUL；APK sha256 `67af33c9c95aa1ea…` |
| 真机扫描/停止/重启 | motorola edge 60 pro / API35 | `scan started gen=1`→发现3台→停止→`scan started gen=2`；无 FATAL（`.rea-captures/round7_ble03_scan/screen.png`） |

> Gradle 层边界（app preBuild 10 项：Shell/Ai/Device/MainThreadIo/ComponentOwnership/ExplicitImports/FrameworkToast/SingleYoLogInit/AppSdkProviderBoundary/AppMvvmLayers）随 `assembleDebug` 全通过。

---

## 4. 残余风险 / 建议后续（均非阻塞）

| ID | 级别 | 说明 | 建议 |
|---|---|---|---|
| ~~BLE-03~~ | ✅已修复 | 平台 binder 已移到专用串行 `yo-ble-scan-platform` 线程并回投 state；真机扫描/停止/重启正常。 | 弱 ROM 长扫场景持续观察即可。 |
| H-04 真机 | P2 | MTU 感知分片写已实现；目标真机 MTU=495 不触发该分支。 | 低 MTU（23）/长帧命令场景专项验证。 |
| 真 HS 设备 | P2 | 第三方 HS01 广播设备在 CCCD 使能后未确认协议 READY，首屏电量仍 `--`（H-02 已具备落地能力）。 | 用真实 HS01/HS02 设备验证 READY 与电量刷新时延。 |
| CBT 子系统 | P3 | Classic 当前无生产消费者，其 SPP 会话并发/帧化缺陷（CBT-01/02/03）未修。 | 若引入 CBT 产品线再按 `docs/audit/round1_classic.md` 专项处理；保持 BLE/CBT 独立端口。 |
| ScanType | P3 | 当前「设备类型」= 型号过滤（HS01/HS02，均 BLE）。 | 若未来出现 CBT 产品线，新增独立 `CbtDevicePort` + 显式 `ScanType`，严禁塞进 BLE 扫描。 |

---

## 5. 证据索引
- 总报告：`docs/device/round1_device_module_audit.md`（§1 依赖图 / §2 蓝牙 / §3 协议 / §4 应用 / §5 卡死与切换根因 / §6 应用层修复 / §10 连接链路 / §11、§17 清理 / §12 扫描 P1 / §13 API 收敛 / §14 入站拼帧 / §15 长帧分片 / §16 过滤契约）
- 分层审计：`docs/audit/round1_ble.md`、`round1_classic.md`、`round1_protocol.md`
- 真机：`docs/device/round2_device_verification.md`；截图 `.rea-captures/round2_scan*、round3_*、round4_scan_after_libfix、round5_filter_hs02、round6_final_hs02`
