# AI 模块重构记录（v5）

每轮写：改动清单 / 验证结果 / 遗留问题。截图目录：预检 `app/doc/ai/captures/`，真机 `app/doc/device-captures/ai/`。

## 第 0 轮：调研与需求（未改代码）

基线：`main` = `49c2549`。

### 改动清单

| 文件 | 内容 |
|---|---|
| `app/doc/ai/research.md` | 18 个开源客户端的源码调研（提交号、文件:行证据），18 个组件的四问，反面清单，v4 结论复核 |
| `app/doc/ai/requirements.md` | A：编号需求（PLT/CON/MOD/SPD-B/SPD-A/ADV/GEN/CHAT/HIS/CALL/USE/SET/NFR），每条有可判定验收；术语表；数值格式 |
| `app/doc/ai/page-spec.md` | B：全局约定（容器、颜色角色、字号阶梯、七态定义）；五页及公共面板的逐字段数据来源与为空表现、组件位置尺寸/状态/交互/边界；七态矩阵（含截图文件名）；数据模型变更与 MVVM 落点 |
| `app/doc/ai/open-questions.md` | 18 个开放问题，每题有建议与两种选择的代价 |
| `app/doc/ai/refactor-log.md` | 本文件 |

代码、布局、旧文档均未改动。

### 验证结果

- 基线构建（沙箱，Linux）：`./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :lib_ai:testDebugUnitTest` 通过；app 单测 308 个、lib_ai 单测 144 个，全部通过，0 跳过。这是重构前的基线，之后每轮对比。
- 调研证据：克隆在沙箱 `/data/research/src/`（不进仓库），提交号写在 `research.md` §1，可按提交号复查。
- Windows 验收命令（`.\gradlew.bat ...`、`adb install -r`）本轮**未执行**：没有代码改动，且沙箱没有设备。

### 现状调查要点（重写依据）

- UI：`ui/ai/chat`（约 1.9k 行）、`history`（1.3k）、`settings`（3.1k，含平台页）、`call`（1.1k），已有 ViewModel/UiState/Mapper，但对话页速度用总耗时计算（`AiAssistantMessageView`），无首字、无实时速度；平台页无连接状态记录；生成参数全局无作用范围；用量无来源。
- 领域记录缺：连接测试结果、测试模型、模型类型/已下线、测速配置指纹、全局参数范围、消息首字/思考时长/平台模型 id、用量来源与费用（见 `page-spec.md` §9.1）。
- 文案混用"令牌"、"API Key"、"密钥"，需统一（术语表）。
- 没有网络状态端口，离线态需要新增 `AiNetworkMonitor`。

### 遗留问题

1. `open-questions.md` 待拍板；拍板前不动代码。
2. 旧文档（`ai-module-*.md`、`ai-architecture.md`、`ui-design.md` AI 部分）在代码重写那一轮移到 `app/doc/archive/`。
3. 真机截图需要你在 Windows 上执行（Q1）。
4. `YoLatencyProbe` 是否在首片段后断开，第一轮实施时验证（`page-spec.md` §9.1）。

### 计划的实施顺序（拍板后）

| 轮 | 内容 | 交付 |
|---|---|---|
| 1 | 备份标签 `ai-ui-v4-backup`；领域字段＋兼容构造＋旧 JSON 样本测试；Policy/Mapper/格式化及单测；`AiNetworkMonitor` | 构建＋单测通过 |
| 2 | 平台页（连接、模型、测速、高级、危险区） | 预检截图＋自查表 |
| 3 | 设置页（含全局参数、说话方式、用量面板） | 同上 |
| 4 | 对话页＋模型面板 | 同上 |
| 5 | 历史面板 | 同上 |
| 6 | 通话页 | 同上 |
| 7 | 文案统一、旧文档归档、架构依赖测试、真机截图脚本 | 全量验收 |
