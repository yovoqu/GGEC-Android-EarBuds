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

## 第 1 轮：领域、数据与 lib_ai（界面未重写）

拍板：`open-questions.md` Q1–Q18 全部按"建议"执行（见该文件顶部）。备份标签 `ai-ui-v4-backup` 指向重构前的 `056bd7d`。

### 改动清单

| 层 | 内容 |
|---|---|
| 领域记录 | `AiModel` 加类型 `AiModelKind`、已下线 `remoteMissing`；`AiPlatformConfig` 加测试模型、连接结果 `AiConnectionResult`；`AiSpeedResult` 加配置指纹；`AiChatPreferences` 加全局参数作用范围 `AiParamsScope` 与模型清单；`AiMessage` 加首字、思考时长、平台/模型 id，新状态 `INTERRUPTED`；`AiUsageRecord` 加来源 `AiUsageSource`；新 `AiFetchedModels`、`AiUsageReport`。旧构造函数全部保留并给默认值（NFR-03）。 |
| 规则（纯函数） | `AiFingerprint`（含 `stale`）、`AiConnectionPolicy`（前置条件、测试模型、状态、失败指向）、`AiSpeedPolicy`（token 估算、实时/平均速度）、`AiBatchSpeedPolicy`、`AiParamsPolicy`（重写：逐项来源、协议裁剪、范围）、`AiSendPolicy`、`AiFailureActionPolicy`、`AiHistoryGrouping`、`AiHistorySearch`、`AiPlatformNamePolicy`、`AiEndpointPreview`、`AiModelDraftPolicy`。`AiContextPolicy` 只带已完成的回答；`AiMessageRecovery` 把"生成中"收尾为"未完成"并保留空消息。 |
| 用例 | `AddPlatform`（同模板多份、最小序号命名）、`EditPlatform`（改名三种错误、API Key、接口地址等于默认存为默认、协议、测试模型）、`ManageModels`（拉取不自动添加、已下线标记、手动添加 K×1000、编辑、删除含当前、删除测速失败的、设为当前、单模型参数）、`AiPlatformJobs`（应用级连接测试与测速：去重、批次进度、取消不写结果不记用量、结果带指纹、记用量来源）、`LoadUsageReport`、`RecordUsage` 带来源。 |
| 会话 | `AiChatSession` 记首字/思考时长/来源，参数走新 `AiParamsPolicy`，新增 `networkLost()`（生成中断网转 NETWORK 失败）；新 `AiCallChat`：通话专用会话，内容只在内存，用量来源"通话"（CALL-10）。 |
| 端口与平台 | 新 `AiNetworkMonitor` 端口与 `platform/ai/AndroidNetworkMonitor`（默认网络回调，判 INTERNET 能力）；清单加 `ACCESS_NETWORK_STATE`。 |
| 数据 | JSON 读写新字段（缺省即默认）；`LibAiModels.models` 返回全部模型、保持接口顺序并映射类型；测速网关 15 s 超时、提示词 "Hi"、只等首字；平台目录自定义模板中文名。 |
| lib_ai | `YoProbePlan.firstTokenOnly(true)`：首个事件到达即结束请求（新测试 `firstTokenOnlyStopsAtTheFirstChunk`）。 |
| 装配 | `AiGraph` 注册 `AiNetworkMonitor`（断网 → `session.networkLost()`）、`AiPlatformJobs` 单例；通话改用 `AiCallChat`。 |
| 旧界面 | 只做保持编译的最小补丁（新错误码有独立文案、`INTERRUPTED` 按"已停止"显示），下一轮起按页删除重写。 |

### 新增/改写的测试

| 测试类 | 覆盖需求 |
|---|---|
| `PlatformManagementTest` | PLT-01/02/03/04/06、MOD-06/07/08/09/11/12、GEN-01/03、ADV-04（协议限制） |
| `AiPlatformJobsTest` | CON-02/06/07、SPD-B-03/05/07/08、MOD-10（只测对话模型） |
| `AiPlatformRulesTest` | CON-03/04/05、PLT-03/04、ADV-02、MOD-08、SPD-B-04/06 |
| `AiChatHistoryRulesTest` | SPD-A-02/03、CHAT-04/10、HIS-02/04 |
| `AiUsageReportTest` | USE-02/03/04/05/07 |
| `AiLegacyJsonTest`（样本 `src/test/resources/ai/legacy/`） | NFR-03、CHAT-15、USE-05 |
| `AiParamsPolicyTest`（重写） | GEN-02/04/05（48 组合表） |
| `AiChatSessionTest`（补） | SPD-A-01/04、CHAT-08/12 |
| `AiCallChatTest` | CALL-10 |
| `LibAiGatewayTest`（改） | MOD-06（全部模型、接口顺序、类型）、ADV-04（每个模板的协议集合） |
| `LatencyProbeTest`（lib_ai，补） | SPD-B-01 |

### 验证结果

- 沙箱 `:app:assembleDebug :app:testDebugUnitTest :lib_ai:testDebugUnitTest`：构建通过；app 单测 372 个（基线 308）、lib_ai 145 个（基线 144），全部通过。
- lib_ai `ResponsesTest.webSocketSendsWhileAResponseIsOpen` 在构建与测试并行时偶发 "Gave up waiting for queue to shut down"（MockWebServer 关闭 WebSocket 超时），单独重跑通过；本轮没有改动该路径，记为已知不稳定测试。
- Windows 验收命令与真机本轮未执行（界面没有变化）。

### 与规格的差异

1. **测速探针原先不在首字后断开**（第 0 轮遗留 4）：`YoLatencyProbe` 会把整段回答读完。已在 lib_ai 加 `firstTokenOnly`，平台页测速与连接测试都打开它，费用只剩首字。
2. **改接口地址不再清空模型列表和测速结果**：旧实现换地址即清空。现在一律保留，由指纹判"需重测/过期"，改回原值即恢复（CON-06、SPD-B-07）。
3. **已停止的回答不再进入上下文**：CHAT-14 写"带最近 N 条成功消息"，旧实现会带上"已停止"的半截回答。
4. **用量费用不分币种**：`AiUsageRecord` 只有金额没有币种，USE-03 的"多币种分列"无数据可依，按单一合计显示；平台开始回报币种后再分列。
5. **旧版测速结果没有指纹**：读入后不判过期（否则升级后所有结果一起变"过期"），下次测速后带上指纹。
6. **通话不再写入对话历史**（Q8）：v4 里级联通话复用对话页会话、内容进历史；现在每次通话一个内存会话。
7. **离线判定不看 VALIDATED**：部分代理/内网环境永远过不了系统连通性检测，按它判会误报离线；真不通由请求失败给出"网络"分类。
8. 旧用例 `SavePlatform`、`FetchPlatformModels`、`AddManualModel`、`RunSpeedTest` 等暂留给旧平台页，第 2 轮随旧页面一起删除。
