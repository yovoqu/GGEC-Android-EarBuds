# 实施记录（2026-11 设备域重画 · app）

> 权威契约：`design.md`（同目录）。审查结论：`audit.md`。运行证据：`verification.md`。
> 写作用域：`app/**`、`app/build.gradle`、`docs/**`。`biz_appupdate`、`lib_*`、`yotheme` 未改。

## 1. 本轮做了什么

把 app 的设备域从「单台设备的内存快照」重画为「**设备台账 + 单活动会话 + 每设备档案**」，并把旧路径一次删干净：

| 目标 | 落地 |
|------|------|
| 多设备管理 | `DeviceRoster`（不可变台账，活动指针 + 按最后同步排序）；`DeviceRosterRepository`（单写者、合并写盘、损坏隔离） |
| 每台设备状态/信息 | `DeviceRecord` 一条承载身份、别名、状态来源（LIVE/ARCHIVED/DISCOVERED）、设备事实（电量/固件/佩戴/耳色）、能力值 |
| 单活动会话 | `DeviceSession` 持代数：先断后连、跨代终态丢弃、被取代请求结算 `REJECTED` |
| 实时/档案不混淆 | `ObserveDeviceRoster` 只在 CONNECTING/READY 覆盖；`SyncActiveDevice` 在掉线时写 ARCHIVED 并保留最后已知事实 |
| UI | 设备页签改为台账页：行内 `⋯` → 重命名/忘记；点行 → 打开设备，成功进设置页；空台账有独立空态 |
| 架构清理 | 删 `DeviceIntake`、单设备首页、`app/ui/OneShotLiveData`、`app/doc/**`、旧审计文档、5 个空目录；`JsonValues` 上移单源 |
| 门禁 | 加 R6 基线放行、`checkNoBaselineCopies`、domain 禁 JSON、ui 禁文件 IO、data/device/json 边界；删假项 |

## 2. 新增文件（app main）

```
domain/device/model   DeviceId / DeviceIdentity / RecordOrigin / DeviceStatus / DeviceFacts
                      DeviceRecord / DeviceRoster / DeviceTarget
domain/device/port    DeviceLinkPort（原 DevicePort 收窄）/ DeviceRosterRepository
                      ScanPermissionRequest / ScanPermissionResult（从 ui 上移）
domain/device/session DeviceSession
domain/device/usecase OpenDevice / CloseDevice / ObserveDeviceRoster / SyncActiveDevice
                      RenameDevice / ForgetDevice
data/json             JsonValues（单源，AI 三个文件改 import）
data/device/json      DeviceRosterJson（DTO 编解码 + 原子写 + 隔离）
data/device/roster    DeviceRosterRepositoryImpl（内存发布 + 合并写盘）
provider/biz_ggec_hs  HsDeviceLinkAdapter（原 HsDevicePortAdapter，删仲裁与 DeviceIntake）
ui/device/roster      DeviceRosterUiState / DeviceRosterUiMapper / DeviceRosterViewModel
                      DeviceRosterEvent / DeviceRosterPage / DeviceRosterRowView / DeviceRosterDialogs
ui/device/settings    DeviceSettingsEvent
```

测试新增 8 个套件（见 `verification.md` §2）+ `testing/device` 三个测试桩（Fake 链路端口、内存台账、快照夹具）。

## 3. 删除/替换

| 删除 | 原因 |
|------|------|
| `domain/device/model/DeviceIntake` | 恒为 `CONNECTION` 的假开关，导致首页永远空（P0） |
| `domain/device/port/DevicePort` | 改为 `DeviceLinkPort` + `DeviceTarget`（身份不再 5 参数散装） |
| `domain/device/usecase/ConnectDevice` | 由 `OpenDevice`（就绪直返 / 缺经典地址显式失败）取代 |
| `provider/biz_ggec_hs/HsDevicePortAdapter` | 由 `HsDeviceLinkAdapter` 取代；适配层不再判「谁被取代」 |
| `ui/device/home/**`（7 文件）与其测试 | 单设备摘要页被台账页替换 |
| `ui/OneShotLiveData` | 与 `lib_common.event` 逐字重复（R6 禁第二份） |
| `ui/device/scan/ScanPermissionRequest|Result` | 端口上移到 domain，修依赖倒置 |
| `app/doc/**`（全部旧架构/需求稿/草稿/`.bak`） | 与当前契约冲突（v5 引用已删门禁） |
| `docs/` 下 8 个旧报告 + `docs/{audit,device,ai}/**` | 描述已删除的旧结构 |
| 5 个空目录 | `model/`、`view/`、`viewmodel/`、`domain/device/scan/`、`ui/device/settings/page/` |

## 4. 数据链路（实现后）

**打开设备**：`DeviceRosterPage` 行点击 → `DeviceRosterViewModel.open(key)` → `OpenDevice.execute(DeviceTarget)` → `DeviceSession.open`（gen++ / 先断）→ `DeviceLinkPort.connect` → `HsDeviceLinkAdapter` → `YoHsSdk.connect` → `ConnectSettlement.Ready` → `OpenSucceeded` 事件 → 宿主打开 `DeviceSettingsActivity(MUSIC)`。
失败：`OpenFailed(failure)` → `DeviceFailureText` 文案 toast；台账不变。

**上报与落盘**：`HsControl.Listener.onSnapshotChanged` → 适配器投影 `DeviceSnapshot` → `DeviceSession`（按代转发）→ `SyncActiveDevice` 合并进台账（别名保留、READY 才更新事实）→ `DeviceRosterRepositoryImpl.update`（内存发布）→ `device-store` 串行执行器合并写 `files/devices/roster.json`（临时文件 + `ATOMIC_MOVE`）。

**冷启动**：`AppContainer` → `DeviceGraph` 构造（读盘、建会话、`SyncActiveDevice.start()`）→ `DeviceRosterViewModel.start()` → `ObserveDeviceRoster` 投影 → 台账页渲染（全部 ARCHIVED）。

## 5. 线程与存储语义

- 落盘只在 `AppExecutors.deviceStore()`（单线程、daemon、core 超时）；主线程不碰 JSON/文件。
- `update()` 在调用线程原子发布；观察者必须快速返回（ViewModel 用 `postValue`）。
- 读取失败：台账以空集启动、坏文件挪到 `roster.json.bad`、`startupFailure()` 与 `observeFailures()` 上报 `STORE_FAILED`，UI 发一次性提示。

## 6. 门禁变更（`app/build.gradle`）

| 门禁 | 变更 |
|------|------|
| `checkAppDependencyBoundary` | 加 `platformBaselinePackages = ['com.yovo.lib_common.event']`（R6）；`providerDependencyModules` 删 `lib_bluetooth` |
| `checkAppLayering` | `domain/**` 禁 `org.json`；`ui/**` 禁 `java.io.File`/`FileOutputStream`/`Files` |
| `checkAiLayerImports` | 删掉扫不存在的 `data/ai/gateway` 的空扫项，改为扫整个 `data/ai/**` 且禁 import `domain.ai.policy`（真规则，不再空转） |
| `checkNoBaselineCopies` | 新增：app 内不得出现 `OneShotLiveData.java` |
| `preBuild` | 依赖列表加 `checkNoBaselineCopies`（共 10 条） |

## 7. 验证

见 `verification.md`：`preBuild` 10 门禁全绿；66 suites / 477 tests / 0 失败；真机（motorola edge 60 pro）完成空台账、扫描、双设备台账冷启动读盘、行内菜单四段观察；重命名输入框与真实耳机链路未取得运行证据，已注明。

## 8. 本刀未做（已在 `design.md` §13 另声明范围）

1. AI 实现层：**CLEAN**。`AiModel` 分家（`StoredModel` 只存 `id + categoryId`）；P2 五项逐项裁定（薄用例保留为缝合面并加门禁、无测试专用构造器、`AiContentPart.Image/Audio` 属 sealed 层次、12 处 catch 均为显式隔离点、默认系统提示词属领域默认值）。
   已闭合：损坏即清空（P0）、错误映射/脱敏单源、`AiSpeedResult` 旧构造器与 token 丢失、密钥别名单源、provider 策略门禁、模型筛选单点、厂商路径进平台目录。
2. AI 界面层：**P0/P1/P2 全部闭合**（尾注/分区标题归 `AiFormat`、输入区尺寸归 `AiTheme`、删除 `status_bar_height` 反射取值，只用 insets）。
3. 设备 UI 余项：`ScanScreen` 的释放通道统一（`SettingsRowsView` 签名守卫与 `AlertDialog` 双轨已闭合）。
   门禁新增：`ui/**` 禁止 `androidx.appcompat.app.AlertDialog`（弹窗原语只有 YoUI 一套）。
4. `biz_appupdate` 接入壳（当前 0 引用，用户明确暂不处理）。
