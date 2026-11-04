# 设备模块需求规格 v0.2

> 状态：草案，待 YG 评审
> 日期：2026-11-03
> 范围：`:app` 内 `device` 域（设备页签 + 扫描连接 + 功能设置页群）
> 依据：`yobtservice` SDK 文档 V2.0.2.4、HS01 参考 APP 信息架构、当前工作区 `app-architecture-v5`

---

## 0. 责任边界（最重要）

**APP 是完整抽象层。协议细节全部由 SDK 处理，APP 不知道也不该知道。**

| 归 SDK | 归 APP |
| --- | --- |
| FeatureCode、market、广播解析、扫描过滤 | 只认「项目名称」= 前缀 + 设备类型（HS + 01 → HS01） |
| BLE / 经典蓝牙 的扫描器选择与连接方式 | 知道 01 这个项目名称下有几个变种 |
| 指令编解码、超时、重试、鉴权 | 按「项目名称 + 变种」取出能力清单（Capability） |
| 上报原始字段到标准状态的解析 | 按能力清单渲染 UI、下发用户意图 |

APP 侧对外的全部机型知识就两件事：**`HS01` 有几个变种**，以及**每个变种支持哪些能力**。其余一律由 SDK 归一化后送进来。

## 1. 目标

做一个多型号耳机的通用管理 APP 的设备部分：

1. 以「项目名称」描述一类耳机（HS01 / HS02 / WY01）。
2. 同一套 UI 通过**能力矩阵**适配：公共组件复用，差异能力按 Profile 裁剪或替换。
3. 首版只做完整 UI 与状态机，接假数据源，不接真实蓝牙。
4. 严格 MVVM，沿用现有分层与 Gradle 守卫。

## 2. 术语

| 术语 | 含义 |
| --- | --- |
| 项目名称 DeviceModel | 项目前缀 + 设备类型，如 `HS`+`01`=HS01、`HS`+`02`=HS02、`WY`+`01`=WY01。**APP 唯一对外可见的机型标识** |
| 变种 DeviceVariant | 同一项目名称下的一个版本标识。**由 SDK 上报，APP 只当作不透明 ID 使用，不解释其来源** |
| 变种数量 | 每个项目名称下有几个变种，是 APP 需要知道的唯一"结构信息"（HS01=3，HS02=1，WY01=待定） |
| Profile | 「项目名称 + 变种」对应的能力声明与参数表 |
| Capability | 能力原子，UI 组件按它渲染 |
| 标准状态 Snapshot | SDK 归一化后的设备状态（连接/电量/佩戴/固件等），APP 只读 |

## 3. 项目与变种（已确认）

APP 只暴露项目名称；一个项目名称可挂多个变种；变种标识由 SDK 给，APP 不关心它背后是区域版还是硬件版。

| 项目名称 | 前缀 | 设备类型 | 变种数 | 首版 |
| --- | --- | --- | --- | --- |
| HS01 | HS | 01 | 3 | 实现 |
| HS02 | HS | 02 | 1 | 实现（能力超集） |
| WY01 | WY | 01 | 待定 | 仅 Profile 占位 |

- 变种之间只通过 `DeviceVariant` 的标识区分，`DeviceModel` 之上不含任何区域/市场信息。
- HS02 相对 HS01 新增能力：双连接管理、10 段自定义 EQ、V12 首页快照、双击/三击左右耳区分、双触、语音助手状态、新手模式、同声传译、佩戴检测提示音、LDAC 查询、EQ 运动/自定义模式、服务状态查询。
- WY01 首版只建空 Profile 与能力声明，不参与扫描识别。

> 具体某个变种对应哪段 FeatureCode、哪个 market、哪种扫描方式，全部写在 SDK 侧；本文档不记录、APP 代码不出现。

## 4. 扫描与连接（APP 视角）

APP 的扫描页只做三件事：调 SDK 的抽象扫描接口、渲染 SDK 回传的结果、把用户选择交回 SDK。

```
用户点"扫描"
   → APP 调 DevicePort.startScan()
   → SDK 内部自行决定 BLE modern / BLE old / 经典蓝牙 的扫描器与过滤策略
   → SDK 回传 ScanResult { modelId, variantId, name, mac, rssi, paired }
   → APP 按 modelId/variantId 查 Profile，渲染"机型名 + 变种 + 信号 + 是否已配对"
   → 用户点连接 → APP 调 DevicePort.connect(modelId, variantId, mac)
   → SDK 完成建链与就绪，回传标准 Snapshot
```

- APP 不做过滤判断、不解析广播字节、不知道 FeatureCode 与 market。
- `ScanResult` 里唯一的机型信息就是 `modelId` + `variantId`，两者都由 SDK 归一化后给出。
- 扫描页交互参考成熟商业 APP：蓝牙开关态、权限态、扫描中动画、结果列表（我的设备 / 可用设备分组）、空态、超时态、错误态。

## 5. 能力矩阵（Capability）

```
CONNECTION          扫描 / 连接 / 回连 / 就绪 / 断开
DUAL_CONNECTION     双连接开关 / 优先设备 / 已配对手机列表 / 指定连接断开   (HS02)
BATTERY             左耳 / 右耳 / 充电盒电量 / 充电中 / 在盒状态
SOUND_QUALITY       音质模式（高音质/高续航）/ LDAC / 音量自适应
EQ_MODE             音乐/电影/新闻/老人/运动/自定义
CUSTOM_EQ           10 段增益 60~16k，取值 0~24，dB=(g-12)*0.5      (HS02)
TOUCH_LONG          长触：录音/语音助手/EQ切换/游戏模式/无
TOUCH_SHORT         短触：语音助手/EQ/游戏/上一首/下一首/音量加/音量减/无
TOUCH_DOUBLE        双触：同短触                                             (HS02)
TOUCH_TAP           双击/三击（V12 分左右耳；V11 不分）
WEAR_DETECT         佩戴检测 / 佩戴提示音
VOICE_WAKEUP        语音唤醒 / 语音助手状态
GAME_MODE           游戏模式
FALL_ALERT          掉落提醒
FIND_EAR            查找耳机 / 找左 / 找右 / 停止
VOLUME_REMIND       听力保护 · 音量提醒
DEVICE_INFO         名称 / 型号 / 固件版本 / MAC / 颜色
RENAME              设备改名
CLEAR_PAIRING       清除配对
RECORD_LOCAL        本地录音开始 / 结束
RECORD_STREAM       随录随传（MP3 落盘）
RECORD_LIST         录音数量 / 列表（分页 ≤12）/ 容量
RECORD_TRANSFER     同步录音文件 / 取消 / 删除 / 清空
AI_ASSISTANT        PCM 流式（16k 单声道 / 1280B 帧）
SERVICE_STATUS      B5：音乐 / 通话 / 录音 / 导出 / 随录随传 / 佩戴 / AI 翻译
BEGINNER_MODE       进入 / 退出 / 操作上报                             (HS02)
INTERPRET           同声传译 进入 / 退出 / 查询                        (HS02)
OTA                 可升级校验 / 进度 / 状态 / 取消
OFFLINE_LOG         离线日志开关
CLASSIC_BT          经典蓝牙连接态监听
```

`DeviceProfile` 携带：项目名称、变种、Capability 集合、参数表（EQ 频段数、触控功能码表、电量字段映射、是否支持 B5、是否支持双连接等）。

- FAQ：B 项（HS01 三个变种是否有能力差异）默认按无差异处理，仅参数不同。

## 6. 页面与信息架构（已确认方向：沿用 AI 页令牌体系）

### 6.1 设备页签（`device`）

1. 设备卡：机型名（HS01）、连接状态、左/右/盒电量、佩戴状态、充电中
2. 快捷区：常用开关（游戏模式等按 Capability 裁剪）
3. 功能入口列表：按 Capability 生成，未支持项按第 8 节策略处理
4. 底部：固件版本 / 关于设备入口

### 6.2 二级页面

| 页面 | 承载能力 |
| --- | --- |
| 扫描连接页 | CONNECTION |
| 音乐与音效 | SOUND_QUALITY / EQ_MODE / CUSTOM_EQ |
| 控制设置 | TOUCH_LONG / TOUCH_SHORT / TOUCH_DOUBLE / TOUCH_TAP |
| 功能设置 | WEAR_DETECT / VOICE_WAKEUP / GAME_MODE / FALL_ALERT / FIND_EAR / VOLUME_REMIND / BEGINNER_MODE / INTERPRET |
| 双连接 | DUAL_CONNECTION |
| 录音 | RECORD_LOCAL / RECORD_STREAM / RECORD_LIST / RECORD_TRANSFER |
| 关于设备 | DEVICE_INFO / RENAME / CLEAR_PAIRING / OTA / OFFLINE_LOG |

- 设备页签形态默认：设备卡 + 快捷区 + 功能入口列表（C 项）。

## 7. 组件清单

**公共（复用）**：连接状态卡、电量卡、功能入口行、开关行、分段选择行、滑块行、选项列表行、空态、加载态、错误条。

**定制（按 Profile 替换）**：EQ 频段编辑器（仅自定义 EQ 机型）、触控动作矩阵（不分左右耳 / 分左右耳两套）、双连接设备列表、录音文件列表。

**原子组件**：只用 YoUI（`YoSwitch` / `YoSlider` / `YoRadio` / `YoSegmentButton` / `YoList` / `YoMenu` / `YoDialog` / `YoModalPanel` / `YoTabs` / `YoToast`）。

## 8. 状态策略

- 三态：`SUPPORTED` / `UNSUPPORTED_HIDDEN` / `UNSUPPORTED_DISABLED`
- 首版默认：未支持项**隐藏**，但在「关于设备」保留能力清单可查（D 项）。
- 连接态：`IDLE` / `SCANNING` / `CONNECTING` / `CONNECTED_NOT_READY` / `READY` / `DISCONNECTED`
- 未连接态首页默认：空态引导 + 最近设备（E 项）。

## 9. 分层与工程约束

沿用 `app-architecture-v5`：

```
ui/device      View / Screen / ViewModel / UiMapper / UiState
domain/device  model(DeviceModel, DeviceVariant, CapabilitySet, DeviceProfile, DeviceSnapshot)
               port(DevicePort, DeviceProfileCatalog, DevicePreferences) / usecase / policy
data/device    FakeDevicePort（假数据）+ ProfileCatalog 实现 + 文件持久化
platform/device 预留真实 SDK 适配位（首版空）
wiring         DeviceGraph 装配
```

- 页面只依赖 `domain` 与 `ui/common`，不碰 `data`。
- ViewModel 不持 `Context`、不建 View、不建线程池。
- YoUI 组件统一由 `YoComponents` scope 释放。
- 新增 `checkDeviceLayerImports` 守卫任务，与现有四项守卫同风格。
- 首版留在 `:app` 按包边界扩展（F 项）。

## 10. 假数据策略

- `FakeDevicePort`：可切换 `CONNECTED` / `DISCONNECTED` / `SCANNING` 演示态。
- 电量、佩戴、服务状态可随机游走以验证 UI 刷新。
- 扫描页可产出多个模拟 `ScanResult`（不同 modelId / variantId）验证分组与 Profile 切换。
- 调试页常驻「演示开关」（G 项）。

## 11. 非目标（首版）

- 真实蓝牙连接与协议收发
- OTA 真实升级
- 后端配置下发
- 多语言（先中文）
- 物理模块拆分

## 12. 待确认汇总

| 编号 | 问题 | 我的默认 |
| --- | --- | --- |
| A | 「我的」里是否允许手动指定项目名称 | 支持，作为 WY01 与无真机调试兜底 |
| B | HS01 三个变种是否有能力差异 | 按无差异处理，仅 Profile 参数不同 |
| C | 设备页签形态 | 设备卡 + 快捷区 + 功能入口列表 |
| D | 不支持能力的表现 | 隐藏，但保留「关于设备」里的能力清单可查 |
| E | 未连接态首页 | 空态引导 + 最近设备 |
| F | 是否新开模块 | 留在 `:app`，加包边界守卫 |
| G | 是否需要演示开关 | 需要，放调试页 |
