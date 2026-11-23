# 设备首页 · 功能清单（参考华为智慧生活 / 音频管家）

> 来源：华为「智慧生活」FreeClip 2 设备页（实机截图）+「音频管家」设备页信息架构。
> 本清单把 APP 现有能力（HS02 目录）**逐项**落到目标版块/页面，不简化、不遗漏。

## 1. 一级页面结构（对齐华为）

| 版块 | 华为示例内容 | 本 APP 现有能力 |
|------|--------------|------------------|
| **设备连接中心** | 已连接状态、电量（左/右/仓）、已连接设备卡片 | CONNECTION、BATTERY、DUAL_CONNECTION、DUAL_CONNECTION_DEVICES |
| **声音** | 空间音频、音效、音质 | SOUND_QUALITY、EQ_MODE、CUSTOM_EQ、LDAC |
| **交互** | 手势控制、头部控制、语音助手 | TOUCH_LONG / TOUCH_SHORT / TOUCH_DOUBLE / TOUCH_TAP、VOICE_WAKEUP、AI_ASSISTANT、GAME_MODE |
| **佩戴** | 佩戴检测 | WEAR_DETECT |
| **更多** | 查找设备、本机设置、固件更新、关于设备 | 见下 |

## 2. 「更多」下的二级页面

| 二级入口 | 华为对应 | 本 APP 能力 |
|----------|----------|-------------|
| **查找设备** | 查找设备 | FIND_EAR（找左/右耳、停止响铃） |
| **本机设置** | 本机设置（设备本地功能开关） | **VOLUME_ADAPTIVE（音量自适应）**、VOLUME_REMIND（音量提醒）、BEGINNER_MODE（新手模式）、INTERPRET（同声传译）、FALL_ALERT（跌落提醒） |
| **固件更新** | 检查更新 | OTA |
| **关于设备** | 关于设备 | DEVICE_INFO、RENAME、REBOOT、CLEAR_PAIRING、CLASSIC_BT、OFFLINE_LOG |
| （录音） | —（本 APP 独立产品能力） | RECORD_LOCAL / RECORD_STREAM / RECORD_LIST / RECORD_TRANSFER |

## 3. 关键结论：音量自适应归「本机设置」

- **音量自适应（VOLUME_ADAPTIVE）属于设备本机设置，不是「声音」版块的快捷开关。**
- 已从首页「声音」版块移除该内联开关（`DeviceHomeUiMapper` 的 `QUICK_TOGGLE_IDS` 与 `声音` 版块不再含它）。
- 目标落位：`更多 → 本机设置` 页（与本表一致）。本轮先做到「不在声音区误放」；本机设置页作为下一步独立导航页承载上述本机开关。

## 4. 图标（多彩）

每个功能一个专属色相（蓝/橙/紫/青绿/玫红/靛蓝/琥珀/绿/青），形状统一取 Phosphor Fill；
详见 [icons.md](icons.md)。

## 5. 当前落地状态

- 一级五版块：已实现（设备连接中心 / 声音 / 交互 / 佩戴 / 更多）。
- 声音：仅「音乐与音效」入口；音量自适应已移出首页。
- 交互：游戏模式（开关）+ 控制设置入口；佩戴：佩戴检测（开关）；更多：功能设置 + 录音 | 关于设备。
- 待建设（下一阶段，另立范围）：`更多 → 本机设置` 二级页，承载音量自适应等本机功能；固件更新入口。
