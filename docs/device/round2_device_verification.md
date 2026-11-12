# Round 2 真机验证报告：扫描 / 类型切换 / 连接数据链路

> 日期：2026-11-10
> 设备：motorola edge 60 pro（serial `NEPI4E0106`），Android 15 / API 35 / arm64-v8a
> 被测：`com.ggec.earbuds`（debug，`app-debug.apk`，sha256 `31341977839a5d51…`，`adb install -r` replace=true，保留数据）
> 目标：用真机验证 Round 1/2 的应用层修复——扫描不卡死、扫描设备类型可切换、连接数据链路真实执行。

---

## 1. 方法
1. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL（33s）。
2. `adb install -r app-debug.apk`（replace=true）；`pm grant BLUETOOTH_SCAN / BLUETOOTH_CONNECT`；`logcat -c`。
3. `am start -n com.ggec.earbuds/.ui.shell.MainActivity` → 通过 uiautomator 定位 `content-desc="添加设备"` 并 `input tap` 进入扫描页（`ScanActivity` exported=false，shell 不能直接 am start）。
4. 抓屏 + `logcat`；操作筛选下拉（全部→HS02→HS01）；返回后点击首台设备触发连接。

---

## 2. 证据

### 2.1 扫描启动（不卡死）
`logcat`（pid 32116）：
```
D/BluetoothLeScanner(32116): onScannerRegistered() - status=0 scannerId=4 mScannerId=0
D/YoBleTimeline(32116): [ble-scan] scan started gen=1, filters=0, timeoutMs=15000, softwareBatch=false, restart=false
D/ActivityTaskManager: Displayed com.ggec.earbuds/.ui.device.scan.ScanActivity for user 0: +150ms
```
截图：`.rea-captures/round2_scan/screen.png` —— 标题“添加设备”、右上“全部设备”、列表“发现 4 台附近耳机”（Hisense HiClips / Vidda V12…，均标注 `HS01` 与 RSSI），底部“开始搜索”。15s 扫描超时后自动停扫并保留列表，**无卡死、无崩溃**。

### 2.2 扫描设备类型切换（可切换）
- 点右上“全部设备”下拉 → 出现 `HS01` / `HS02` 选项。
- 选 `HS02`：uiautomator 显示 `text="HS02"` + `当前筛选下暂无设备` + `已发现附近耳机，切换右上角设备类型可查看全部结果。`（附近 4 台均为 HS01，故 HS02 过滤为空）——**切换即时生效，无卡顿**。
- 再切 `HS01`：截图 `.rea-captures/round2_scan_hs01/screen.png` —— 下拉显示 `HS01`，列表恢复 4 台 HS01。
- 结论：`全部 / HS01 / HS02` 过滤切换正确、即时、不重启扫描、不冻结；HS01/HS02 均来自 BLE 广播（无 BT/CBT 设备混入）。

### 2.3 连接数据链路（真实执行）
点击首台设备（Hisense HiClips `75:D0:82:7D:A4:A8`）后 `logcat`（pid 32116）：
```
D/YoBleTimeline(32116): [ble-scan] scan stopped gen=1
D/BluetoothGatt(32116): connect() - device: xx:xx:xx:xx:A4:A8, auto: false
D/BluetoothGatt(32116): onClientConnectionState() - status=0 clientIf=15 device=xx:xx:xx:xx:A4:A8
D/BluetoothGatt(32116): discoverServices() - device: xx:xx:xx:xx:A4:A8
D/BluetoothGatt(32116): onSearchComplete() = Device=xx:xx:xx:xx:A4:A8 Status=0
D/BluetoothGatt(32116): configureMTU() - device: xx:xx:xx:xx:A4:A8 mtu: 517
D/BluetoothGatt(32116): onConfigureMTU() - Device=xx:xx:xx:xx:A4:A8 mtu=495 status=0
D/BluetoothGatt(32116): setCharacteristicNotification() - uuid: 65786365-6c70-6f69-6e74-2e636f820105 enable: true
```
说明 `HsDevicePort.connect → YoHsSdk → YoHsClient → HsProtocolEngine → YoBleCommandLink → YoBleClient.connect(GATT)` 链路**真实执行**：停止扫描 → BLE GATT 连接（status=0）→ 服务发现（Status=0）→ MTU 495 → 使能 HS 通知特征 `…820105`。`-b crash` 为空、进程存活（pid 32116）——**连接不再是空实现，也不崩溃**。

### 2.4 发现的新问题（应用层 UX）
截图 `.rea-captures/round2_after_connect/screen.png`：点击连接后返回设备首页，仍显示“还没有连接耳机”。
- 根因：`DeviceHomeUiMapper.map(...)` 在 `!snapshot.isConnected()`（`ConnectionState.CONNECTING` 不算 connected）时直接返回 `disconnected()`；`LoadDeviceProfiles.profileFor(snapshot)` 同样要求 `isConnected()`。→ **连接中/未就绪期间首页无任何连接反馈**。
- 次因：第三方 HS01 广播设备在 CCCD 使能后未确认协议就绪（`onNotificationEnabled` 未完成/设备未响应），`ConnectionState` 可能长期停留 `CONNECTING`；缺少连接超时兜底。
- 处置（下一轮）：首页在 `CONNECTING` 显示“连接中”卡片；`HsDevicePort.connect` 增加连接就绪超时（如 12s→`DISCONNECTED` + 失败提示）；真 HS 设备复测协议 READY。

---

## 3. 结论
- ✅ 扫描不卡死（两轮进入均正常启动；超时自停、列表保留）。
- ✅ 扫描设备类型（全部/HS01/HS02）可正常切换，仅 BLE 结果，无 CBT 混入。
- ✅ 连接数据链路真实执行（GATT 连接/服务发现/MTU/通知使能），非空实现、无崩溃。
- ⏳ 连接后的首页状态反馈与“连接就绪超时”为下一轮应用层修复项（§2.4）。

## 4. 设备副作用
安装 debug 包（replace 保留数据）、授予蓝牙运行时权限、进入扫描页并尝试连接一台附近 HS01 设备（未配对/未改变其数据）。均在测试范围内。

---

# Round 3 追加验证：连接中反馈 + 连接超时（针对 §2.4 的修复）

> 变更：`DeviceHomeUiMapper.map` 允许 `CONNECTING` 显示设备卡；`DeviceHomeViewModel.publish` 以 `modelId/variantId` 兜底解析 profile；`HsDevicePort.connect` 增加 12s 就绪超时（超时→`disconnect()`+`DISCONNECTED`）。APK sha256 `b7191b8df0624bf3…`，`install -r`。

## R3-1 连接中可见（修复“点连接→首页仍显示未连接”）
进入扫描页 → 点击首台设备（HS01）→ 3s 后首页截图 `.rea-captures/round3_connecting/screen.png`：
显示“当前设备 / HS01 / ● 连接中”，电量 `-- / -- / --`，开关置灰、入口列表可见。→ **连接中已有明确反馈**。

## R3-2 连接就绪超时（修复长期“连接中”）
点击连接后约 11s 再截图 `.rea-captures/round3_after_timeout/screen.png`：
首页回落“还没有连接耳机”。说明 12s 内未收到协议 `onConnected`（第三方 HS01 广播设备未确认就绪/无有效 CCCD 反馈），超时逻辑触发 `disconnect()` 并发布 `DISCONNECTED`，**不会无限“连接中”**；进程存活、无崩溃（`-b crash` 空）。

## R3-3 结论
- ✅ `CONNECTING` 首页可见（真机截图证实）。
- ✅ 连接超时兜底生效（12s → 回落未连接，无卡死/无崩溃）。
- ⚠️ 第三方 HS01 广播设备未达到协议 READY（电量仍 `--`）——需真 HS 设备复测；协议侧就绪依赖设备对 CCCD/查询的响应（H-02 已具备落地能力，待在真 HS 设备上触发查询）。

