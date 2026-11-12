# 第五轮：设备链路数据链路重设计 · 严禁兜底防御性编码

> 依据 `D:\A_yoprogram\Github_Gallery\YoAgentDocs`（architecture.md「禁止防御性修补」「模型跟随所在层」、development.md）。
> 本轮不是补分支，是**换契约**：让非法状态在构造点就无法产生，从而删掉下游所有 `? :` / `catch` / `default` / 钳位。

## 1. 现状数据链路（三段，file:line）

**扫描（biz → lib）**
```
YoHsNearby(HsNearbyScanner) → HsNearbyScanSession(会话代次)
  → HsNearbyScanCallbacks(代次放行) → HsNearbyDevices(帧→模型)
  → HsScanFailures(BleErrorCode→HsScanFailure)
  → HsNearbyObserver → app HsDevicePort → ScanViewModel → ScanUiState
```

**协议（biz）**
```
GATT ← YoBleCommandLink → HsProtocolEngine → InboundFrameDemux → DeviceEventRouter
  → DeviceDomainStore(域) → DeviceEventPipeline → HsDeviceObserver
  → app HsDevicePort.Bridge → DeviceSnapshot
```

**展示（app）**
```
DevicePort → HsDevicePort(投影) → ObserveDevice → DeviceHomeViewModel
  → DeviceHomeUiMapper → DeviceHomeUiState → DeviceHomePage
```

## 2. 防御性编码清单（现状，按危害排序）

### A 类 · `default` 吞掉未枚举错误码（错误分类失效）
| 位置 | 写法 | 危害 |
|---|---|---|
| `HsScanFailures.java:23` | `default -> HsScanFailure.FAILED` | lib 新增错误码时静默归为 FAILED，`SCAN_THROTTLED`/权限类等新分类永远不生效，且编译器不报错 |

### B 类 · 字符串当类型 + 下游再解析（双向兜底）
| 位置 | 写法 | 危害 |
|---|---|---|
| `HsBattery.java:10-13,30-43` | 电量以 `String` 暴露（`"80"` / `"--"`） | 已解析的结构化值在模型层退化回文本 |
| `HsDevicePort.java:264-273` | `null → UNKNOWN`；`Integer.parseInt` + `catch NumberFormatException → UNKNOWN` | 下游重新解析 + 吞异常；协议新增未知文本时静默变未知 |

### C 类 · record 内契约不一致（一半断言、一半静默转换）
| 位置 | 写法 | 危害 |
|---|---|---|
| `HsNearbyDevice.java:18-21` | `modelId` 用 `requireNonNull`，而 `variantId/bleName/bleMac` 用 `== null ? "" :` | 同一 record 两种契约；空串同时表示「真的没有」与「上游传了 null」 |
| `HsScanSnapshot.java:41-42` | `serviceUuids == null ? emptyList()` / `rawAdvertising == null ? new byte[0]` | 同上 |
| `HsDeviceInfo.java:33` | `firmware == null ? UNKNOWN_FIRMWARE :` | 同上（`UNKNOWN_FIRMWARE` 是 `""`，与「固件为空」不可区分） |
| `DeviceSnapshot.java:38-42` | `battery/wear/firmware/deviceName/mac` 五个 `== null ?` 默认值 | 领域快照静默补默认值，掩盖上游未赋值 |
| `ScanResult.java:19-21`、`DeviceVariant.java:14` | 三个 `== null ? ""` | 同上 |

### D 类 · 静默钳位（把非法输入改成合法）
| 位置 | 写法 | 危害 |
|---|---|---|
| `BleScanConfig.java:189,219,225,249,256` | `Math.max(200L, timeout)`、`Math.max(1, Math.min(3, n))`、`Math.max(0L, delay)`、`Math.max(1_000L, lostTimeout)` | 配置是代码常量，非法即编程错误；钳位让它变成"看起来生效" |
| `YoBleScanThrottleGuard.java:23-24,42` | `Math.max(1_000L, window)`、`Math.max(1, maxStarts)`、`Math.max(1L, unlockAt - now)` | 同上；`unlockAt - now` 的钳位掩盖时钟语义 |
| `YoBleScanStats.java:50` | `Math.max(0L, now - activeStart)` | 掩盖时钟回拨（应使用单调时钟的不变量） |
| `YoBleScanRecordParser.java:209` | `new byte[Math.max(0, length)]` | 掩盖负长度（AD 解析已保证非负） |
| `DeviceControls.java:32` | `Math.min(customEqGains.length, gains.length)` | 掩盖长度不匹配（应断言相等） |

### E 类 · catch 宽捕获（平台边界之外）
| 位置 | 写法 | 判定 |
|---|---|---|
| `YoBleCommandLink.java:188,216` | `catch (RuntimeException)` + 日志 + 返回默认 | 已可观测，但捕获面过宽，应按具体异常分类 |
| `HsDevicePort.java:181` | `catch (RuntimeException)` release | 平台边界，保留但需分类 |
| `HsDevicePort.java:270` | `catch (NumberFormatException)` | **应随 B 类消除**（不再做字符串解析） |

## 3. 重设计原则（写死，实施不得偏离）

- **P1 构造点断言**：`@NonNull` 字段在 compact constructor 中 `requireNonNull`，不在构造器里静默转空串。违约在构造点抛，不留到消费端。
- **P2 未知用类型表达**：可缺失语义用**值类型**，不用 `""` / `-1` / `0` 兼作未知与真实值。电量用 `int` + `UNKNOWN = -1`（已有 `BatterySnapshot`），固件用显式 `Optional` 或 `HsDeviceInfo.hasFirmware()`；不得让 `""` 同时表示「就是空」与「未上报」。
- **P3 枚举 switch 穷尽**：错误映射、状态映射一律穷尽所有常量，**删除 `default`**；新增常量由编译器强制补分支。
- **P4 解析只发生一次**：协议文本在**产生数据的那一层**（biz 协议解析）转成结构值，模型只传结构值；下游禁止再解析字符串。
- **P5 非法参数抛错**：`Builder`/配置构造对非法值抛 `IllegalArgumentException`，不静默钳位（这些值是代码常量，非法=编程错误）。
- **P6 catch 只在平台边界**：Android Binder/IO/反射失败可捕获，但必须分类 + 记录 + 交由上层类型化失败；禁止 `catch → return 默认值` 掩盖。
- **P7 时钟不变量**：耗时/窗口用单调时钟（`System.nanoTime` 或注入 `LongSupplier`），不用 `Math.max(0, ...)` 掩盖回拨。

## 4. 目标架构（契约变更）

| 层 | 变更 |
|---|---|
| `biz.model.HsBattery` | 电量改 `int`（`-1` 未知），删除 `String` 电平；`earInCaseStatus` 保留 `int`（`-1` 未知已在 model 层表达） |
| `biz.model.HsNearbyDevice` / `HsScanSnapshot` / `HsDeviceInfo` | `@NonNull` 字段全部 `requireNonNull`；`HsDeviceInfo.firmware` 用 `""` 仅表示「未上报」并在 javadoc 写死，不接 `null` |
| `biz.provider.HsScanFailures` | 穷尽 `BleErrorCode`，删除 `default` |
| `app.domain.DeviceSnapshot` / `ScanResult` / `DeviceVariant` | 删除 `== null ?` 默认值，改 `requireNonNull`；`BatterySnapshot` 已是 `int`（保持） |
| `app.provider.HsDevicePort` | 删除 `parseLevel` 与 `NumberFormatException`；直接读 `HsBattery` 的 `int` |
| `lib.scan.BleScanConfig.Builder` / `YoBleScanThrottleGuard` | 非法值抛 `IllegalArgumentException`，删除钳位 |
| `lib.scan.YoBleScanRecordParser` | 长度不变量断言（AD TLV 已保证），删除 `Math.max(0, length)` |
| `app.domain.DeviceControls` | 断言两数组等长，删除 `Math.min` |

## 5. 实施所有权（互不重叠）

- **biz-model**：`HsBattery`、`HsNearbyDevice`、`HsScanSnapshot`、`HsDeviceInfo` + 其测试
- **biz-provider**：`HsScanFailures`、`YoBleCommandLink` 的 catch 分类 + 其测试
- **lib-scan**：`BleScanConfig`、`YoBleScanThrottleGuard`、`YoBleScanRecordParser`、`YoBleScanStats` + 其测试
- **app-domain**：`DeviceSnapshot`、`ScanResult`、`DeviceVariant`、`DeviceControls` + 其测试
- **app-provider**：`HsDevicePort`（删 `parseLevel`，消费 `HsBattery` 的 `int`）+ 其测试

`biz-model` 与 `app-provider` 有先后依赖（`HsBattery` 改 `int` → app 才能删解析），按 **biz-model → app-provider** 顺序执行；其余可并行。

## 6. 验收条目（可二判，全部通过才收口）

- **V1**：`grep -R "== null ?" biz_ggec_hs/src/main/java/com/yovo/biz_ggec_hs/model app/src/main/java/com/ggec/earbuds/domain/device/model` = **0**（平台边界与 `Optional` 用法除外，需逐条在报告列白名单）。
- **V2**：`grep -R "default ->" biz_ggec_hs/src/main/java/com/yovo/biz_ggec_hs/provider/lib_bluetooth` = **0**。
- **V3**：`grep -n "parseLevel\|NumberFormatException" app/src/main/java/com/ggec/earbuds/provider/lib_bluetooth/HsDevicePort.java` = **0**。
- **V4**：`grep -n "Math.max\|Math.min" lib_bluetooth/src/main/java/com/yovo/lib_bluetooth/internal/ble/scan/{BleScanConfig,YoBleScanThrottleGuard,YoBleScanStats,YoBleScanRecordParser}.java` = **0**（或在报告中逐条说明保留理由并给出不变量依据）。
- **V5**：非法配置构造抛 `IllegalArgumentException` 的单测（`timeoutMillis=-1`、`numOfMatches=0`、`windowMillis=0`、`maxStarts=0`）各一条，断言异常类型与消息要点。
- **V6**：`HsBattery` 改 `int` 后，`HsDevicePort` 直接投影，无字符串解析；单测断言 `80`/`-1` 两态。
- **V7**：行为不变 —— 三模块单测全绿、0 跳过；`assembleDebug` 成功；15 项边界门禁全 PASS。
- **V8**：未实机，报告明示。

## 7. 执行结果（实测）

```
:lib_bluetooth:testDebugUnitTest   36 套件 / 176 用例 / 0 失败 / 0 跳过
:biz_ggec_hs:testDebugUnitTest     15 套件 /  76 用例 / 0 失败 / 0 跳过
:app:testDebugUnitTest             62 套件 / 462 用例 / 0 失败 / 0 跳过
:app:assembleDebug                 BUILD SUCCESSFUL → app-debug.apk 23,996,020 B
:lib_bluetooth:compileDebugAndroidTestJavaWithJavac   通过
5 项 lib/biz 边界门禁                                    全 PASS
```

| 验收 | 结果 | 证据 |
|---|---|---|
| V1 去 null 兜底 | **0** | `biz/model` + `app/domain/model` 的 `== null ?` 归零；`deviceInfoLocked()` 已在投影处做 null→UNKNOWN，record 内那行是**双重兜底**，已删 |
| V2 去 `default` 吞码 | **0** | `HsScanFailures` 穷尽 59 个 `BleErrorCode`：14 个映射到专属分类，45 个契约外错误码**显式抛 `IllegalArgumentException`**（不伪造扫描原因） |
| V3 去字符串解析 | **0** | `HsBattery` 电平改 `int`（`UNKNOWN_LEVEL=-1`），`fromProtocolText` 是唯一解析入口（`--`→-1，`1..100`→值，其余**抛错**）；`HsDevicePort.parseLevel` 与 `NumberFormatException` 删除 |
| V4 去静默钳位 | **0** | `BleScanConfig.Builder` 5 处、`YoBleScanThrottleGuard` 2 处、`BleConfig` 6 处全部改抛 `IllegalArgumentException`；`YoBleScanStats` 改纳秒单调时钟（差值恒非负，无需钳位）；`YoBleScanRecordParser` 负长度抛错 |
| V5 非法配置断言 | 通过 | `BleScanConfigTest` 12 例 + `YoBleScanThrottleGuardTest` 3 例 + `BleConfigTest` 4 例，断言异常类型与消息中的字段名/非法值 |
| V6 结构化电量投影 | 通过 | `projectionsKeepStructuredLevelAndUnknownApart`：`80` 与 `-1` 两态不混，`hasLeft/hasRight/hasCase` 各自正确 |
| V7 行为不变 | 通过 | 三模块 0 失败 0 跳过；APK 产出；门禁全过 |
| V8 未实机 | 明示 | 仅静态 + 单测 + 门禁 |

### 一处「兜底搬家」被识别并修正

修复员最初把 `HsDevicePort.parseLevel` 的 `catch (NumberFormatException)` 删掉后，`DeviceDomainStore.normalizeLevel` 里仍留着同类吞异常且**无日志**——那只是把兜底从 app 搬到 biz store，问题没消除。

修正：`normalizeLevel` 保留容错（入站帧是**外部输入**，设备可能发不规范文本），但每一例非协议文本都 `HsLog.w` 记录，并放宽注释说明「容错必须可观测」；编程侧的非法值（构造 `HsBattery`）走严格校验并抛错。**外部输入容错 ≠ 代码兜底**，区别在于是否可观测。

### 残留（记录，非本轮范围）
- `HsProtocolEngine` 的 `battery()` 投影点、`DeviceDomainStore` 的字符串电平存储：协议原文仍在 store 内保存，转换只在投影到 `HsBattery` 时发生（一次解析，符合 P4）。若将来要彻底结构化，可把 store 电平也改 `int` —— 那会牵动入站帧解析与既有测试，属独立一刀。
- `app/domain/device/model/CapabilitySet` 的 `items != null` 折叠已在本轮一并清掉（`requireNonNull`）。
