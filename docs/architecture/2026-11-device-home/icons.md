# 图标方案 · 多源调研（选定 Phosphor Duotone）

> 用户从 10 家第三方库对比中选定 **Phosphor Duotone**（Phosphor 社区，MIT），并采用「每项专属色相」。
> 全库对照图：`design/device-home/icon-library-compare.png`。

## 1. 候选库横向对比（GitHub / Iconify）

| 库 | 作者·组织 | 风格 / 颜色 | 许可 | 星 | 结论 |
|----|-----------|-------------|------|----|------|
| **Phosphor Duotone** | Phosphor 社区 | 双色（主色 + 20% 同色副层） | MIT | 高 | **采用** |
| IconPark | 字节跳动 | 双色/多色 | Apache-2.0 | 9.0k | 备选 |
| Solar Duotone | 480 Design | bold-duotone | CC BY 4.0 | — | 备选 |
| Hugeicons | Hugeicons | 圆头线性 | — | — | 备选 |
| Lucide / Tabler | 社区 / Paweł Kuna | 线性单色 | ISC / MIT | 25.9k / 22.7k | 否（单色） |
| MingCute / Basil / IconaMoon / Unicons | 各自作者 | 单色线/面 | — | — | 否（单色） |
| OpenMoji | HfG + 社区 | 彩色扁平 | CC BY-SA 4.0 | — | 否（多为单色黑字形） |

## 2. 做法：Phosphor Duotone + 每项色相

- 取 Phosphor `assets/duotone/<name>-duotone.svg`（256 viewport，两层：主层 + `opacity=0.2` 副层）。
- 每个图标把 `currentColor` 烘焙成本项专属色相，副层保留 `fillAlpha=0.2` → 天然「主色 + 同色浅填充」的双色。
- 因此既保留 Phosphor Duotone 的双色质感，又做到**每个功能不同色相**（不是单一品牌色）。

## 3. 落地

- `ic_dev_*.xml`（9 枚，PH Duotone 转 Android Vector，颜色烘焙）：
  蓝牙(设备连接中心) 蓝 / 音符(音乐) 橙 / 手指点按(交互) 紫 / 推子(功能设置) 青绿 /
  麦克风(录音) 玫红 / 信息(关于设备) 靛蓝 / 手柄(游戏模式) 琥珀 / 耳朵(佩戴检测) 绿 / 喇叭(音量自适应) 青。
- 渲染：`DeviceCardView` 按矢量自带颜色显示，不做运行时单色 tint；旧图标整套覆盖删除，无并存。
