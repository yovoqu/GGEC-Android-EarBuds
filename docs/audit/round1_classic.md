# lib_bluetooth 经典蓝牙 (CBT) 子系统只读审计 — Round 1

> 审计对象：`lib_bluetooth` 模块的经典蓝牙子系统（`api.YoCbtClient`、`internal/classic/**`、`model.classic/**`）
> 审计方式：**只读**。仅使用 `read` / `grep` / `glob` 读取源码，未修改任何源码。
> 结论均带证据（`相对路径:行号` + 简短引用）。
> 关联：这是“设备模块：蓝牙层 → 协议层 → 应用层”全量审查中“蓝牙层 · CBT 部分”的第一轮。

---

## 1. 概述

CBT 子系统是一个“公开门面 → 内部客户端 → Android 模块依赖图 → 各能力模块”的分层实现，覆盖：
设备发现、配对/解绑、ACL 链路、SDP 服务发现、Profile（A2DP/HEADSET 真实接口 + AVRCP/MAP/OPP/PAN/PBAP/HID/LE_AUDIO/HEARING_AID 扩展）、SPP 多连接与协议命令会话。

结构规模（`glob` 统计，均为 `.java`）：
- `api/YoCbtClient.java`：公开接口，33 个方法。
- `internal/classic/**`：主源码约 190 个文件（含 `internal/classic/internal/android/**` 平台适配层）。
- `model/classic/**`：约 24 个公开模型。
- 测试：`src/test` 与 `src/androidTest` 中有 `YoCbt*` 单测/仪器化测试。

**核心结论（先给）：**
1. 契约完整性：`YoCbtClient` 的 33 个方法在 `ClassicClientImpl` + `YoCbtAndroidClient` 中全部有实现，能力面无缺席；但存在多处**语义/错误码塌缩**与**端点信息丢失**（见 CBT-05/06/07）。
2. CBT 与 BLE **在 import、Runtime、线程池、事件总线层面完全解耦**；唯一共享的进程级可变单例是 `BluetoothDiagnosticsBus`（诊断汇聚口，非命令/控制通道）。
3. 最重要的隐患是**线程模型不一致**：`YoCbtRuntime` 宣传“单线程 state 模型”，但 SPP 连接状态机实际跑在 `io`/回调/调用线程/独立读写线程上，且命令会话内部 `HashMap` 无同步（CBT-01/02/03）。
4. **整个 CBT 子系统当前没有生产消费者**：`biz_ggec_hs` 与 `app` 只使用 BLE；CBT 仅被仪器化测试引用（CBT-14）。

---

## 2. 子系统结构与数据流

### 2.1 分层与装配

```
YoBluetoothSDK.classic(ctx[,config])                // lib_bluetooth/YoBluetoothSDK.java:22-29
  └─ BluetoothSdkCore.classic(ctx, config)          // internal/BluetoothSdkCore.java:25-29
       └─ new ClassicClientImpl(ctx, CbtConfig)     // internal/classic/ClassicClientImpl.java:104-116
            └─ new YoCbtAndroidClient(ctx, internalConfig)   // internal/classic/internal/android/YoCbtAndroidClient.java:90-103
                 └─ YoCbtAndroidModuleGraph.create(...)       // .../YoCbtAndroidModuleGraph.java:186-295
                      ├─ YoCbtRuntime（state/io/callback/scheduler）      :191
                      ├─ discovery / bond / acl（receiver+ticker+repo）    :202-233
                      ├─ SDP（receiver + resolver + serviceCache）         :232-239
                      ├─ profiles（proxyManager + a2dp/headset + extension）:241-268
                      └─ spp（socketFactory + remoteGateway + sppManager + commandSessionRegistry）:270-279
```

- 公开门面 `ClassicClientImpl` 只做“公开模型 ↔ 内部模型”的双向转换，并把旧内部 `CbtInternalClient` 委托为目标（`ClassicClientImpl.java:101-116`）。
- 内部实现 `YoCbtAndroidClient` 把每个能力委托到 `modules.*`（`YoCbtAndroidClient.java:66-103`）。

### 2.2 关键调用链

**（A）发现**
`discover()` → `ClassicClientImpl.java:120-146` → `YoCbtAndroidClient.discover` `:124-144` → `YoCbtDiscoveryController.start` `:68-124`
→ `runtime.state(initiateDiscovery)` `:122` → `discoveryPlatform.startDiscovery()` `:297`
→ 广播 `YoCbtDiscoveryReceiverAdapter.onReceive` → `runtime.state(dispatch)` `receiver/...:58-66` → `onDeviceFound` `:227-267` → `runtime.callback(observer.onDeviceFound)`。
设备结果合并走 `YoCbtDiscoveryResultMerger`（`:256-258`），并写入 `YoCbtDeviceSnapshotStore`。

**（B）配对 / 解绑**
`createBond` → `YoCbtBondOperationController.createBond`（校验 CONNECT 权限）`pairing/YoCbtBondOperationController.java:36-44`
→ `YoCbtAndroidBondOperationPlatform.createBond`（公开 `device.createBond()`）`:43-76`。
`cancelBondProcess` / `removeBond` 走 hidden API：`platform:80-136` → `YoCbtHiddenApiBondManager.invokeBoolean`（反射 `getMethod`）`hiddenapi/...:31-47`。

**（C）SPP 多连接收发**
`connectSpp` → `ClassicClientImpl.java:384-422` → `YoCbtAndroidClient.connectSpp` `:361-408`
（权限检查 `:371-383`，`discoveryController.cancelForConnect()` `:387`）→ `module.sppManager.connect(...)` `spp/connection/YoCbtSppConnectionManager.java:60-107`
→ `runtime.io(() -> connection.open(endpoints))` `:105` → `YoCbtSppConnection.open` `spp/connection/YoCbtSppConnection.java:137-282`
→ `socketFactory.create → connect → getInput/OutputStream → new Reader/Writer → start()` → `transition(CONNECTED)`。
读：`YoCbtSppReader.run` 独立线程 `spp/io/YoCbtSppReader.java:99-160` → `runtime.callback(listener.onDataReceived)` `:168` → `YoCbtAndroidClient.wrapSppObserver.onSppDataReceived` `:480-484` → `commandSession.onBytesReceived`。
写：`send/sendCommand` → `YoCbtSppConnection.write` `:284-300` → `YoCbtSppWriter.write`（入队）`spp/io/YoCbtSppWriter.java:67-108` → 独立写线程 `runLoop` `:154-214` → `OutputStream.write/flush`。

**（D）协议命令会话**
`sendCommand` → `YoCbtAndroidClient.sendCommand` `:527-535` → `YoCbtCommandSession.submit` `protocol/command/YoCbtCommandSession.java:57-87`
→ `codec.encode` → `sender.send` → `queue.enqueue` → `scheduleTimeout` → 到达响应时 `onBytesReceived` `:89-102` → `dispatchPacket` `:122-139` → `queue.matchAndRemove` 匹配 → 回调结果。
超时：`scheduler.schedule(() -> handleTimeout)` `:141-146`；重试在 `handleTimeout`/`retrySend` `:148-186`。

### 2.3 数据流（SPP↔协议）

```
[App] --sendCommand()--> SppConnectionManager.send() --> Connection.write() --> SppWriteQueue --+
                                                                                              |
[SPP socket write thread] <------------------------------------------------------------------ +
[SPP socket] --bytes--> SppReader thread --runtime.callback--> wrappedObserver.onSppDataReceived
    --> CommandSession.onBytesReceived --> codec.decode --> queue.matchAndRemove --> observer.onCommandResult
```
- 注意：命令会话实际使用 `YoCbtRawPacketCodec`（**无帧化**）见 CBT-02；`YoCbtFramedPacketCodec/YoCbtPacketFramer` 生产未接入。

---

## 3. 缺陷 / 风险清单

> 等级：P0=阻断/数据破坏，P1=高概率功能性/并发缺陷，P2=契约瑕疵/冗余/可维护性。

### CBT-01 | P1 | 命令会话内部状态非线程安全
- 位置：`internal/classic/protocol/command/YoCbtCommandSession.java:39-40`（`attempts`/`timeouts` 为普通 `HashMap`）
- 问题：`submit()`（调用方线程）`attempts.put/timeouts.put` `:82-83,145`；`onBytesReceived()`（Reader→callback 线程）`attempts.remove/cancelTimeout` `:132-133`；`handleTimeout()`（scheduler 线程）`attempts.get/put/remove` `:155-164`；`retrySend()` `:169-186`。均未同步，且 `queue` 有 `synchronized` 而这两张表没有。
- 证据：`:39 private final Map<String, Integer> attempts = new HashMap<>();`、`:40 private final Map<String, YoCbtCommandTimeout> timeouts = new HashMap<>();`；多线程入口见上；`YoCbtSppReader.dispatchBatch` 通过 `runtime.callback` 派发 `:168`。
- 影响：并发下可能丢失/错配超时句柄、重试计数错乱、偶发 `ConcurrentModificationException`。
- 建议：把 `attempts/timeouts` 改成 `ConcurrentHashMap`，或将整个会话方法统一收敛到 `runtime.state`。

### CBT-02 | P1 | 命令协议“帧化”未接入生产，粘包/并发命令响应错配
- 位置：`internal/classic/protocol/command/YoCbtCommandSessionRegistry.java:21`（`private final YoCbtRawPacketCodec codec = new YoCbtRawPacketCodec();`）
- 问题：命令会话固定使用 `YoCbtRawPacketCodec`，`onBytesReceived` 会把**一次 read 的整块字节**当作一个数据包（`YoCbtCommandSession.java:89-101`），且默认匹配器为 `YoCbtResponseMatcher.FIRST_PENDING`（`YoCbtCommandRequest.java:62`），即“永远匹配最早一个待处理命令”。
- 证据：`YoCbtFramedPacketCodec`/`YoCbtPacketFramer`（`protocol/codec/YoCbtFramedPacketCodec.java:12-64`、`protocol/packet/YoCbtPacketFramer.java:15-94`）仅在测试与 `YoCbtCommandSession` 的 `instanceof` 分支出现（`YoCbtCommandSession.java:90-93,117-119`），而注册表实例化的是 `YoCbtRawPacketCodec`。
- 影响：一次响应被拆成多段到达时会被拆成“多个包”；多命令并发时响应会返给错误的命令（FIRST_PENDING）。协议“命令↔响应”并未真正闭合，正确性依赖调用方“严格一问一答、单批到达”。
- 建议：提供可配置的编解码工厂，生产命令通道默认启用 `YoCbtFramedPacketCodec`；匹配器改用 `byCommandIdPrefix/ByCommandIdString` 或明确“串行命令”约束并在 API 文档标注。

### CBT-03 | P1 | SPP 连接状态机跨线程，`YoCbtRuntime` 的单线程 state 契约未覆盖 SPP
- 位置：`internal/classic/spp/connection/YoCbtSppConnection.java:137-331`、`YoCbtSppConnectionManager.java:105`
- 问题：`open()` 在 `runtime.io(...)` 上运行并直接 `transition(...)`（`:105` / `YoCbtSppConnection.java:145,170,177,202,207,229,247`）；`fail()` 多由 `runtime.callback` 的读/写错误回调触发（`YoCbtSppConnection.java:214-219,238-243,327-331,345-356`）；`close(true)` 由 `disconnect()` 在**调用方线程**直接执行（`YoCbtSppConnectionManager.java:152-165`）；`close(false)` 由 ACL 事件在 `runtime.state` 线程执行（`YoCbtAndroidClient.java:106-119` → `YoCbtSppConnectionManager.java:177-189`）。`transition()` 直接写 `lifecycle.enter` 并回调观察者（`YoCbtSppConnection.java:320-331`），`YoCbtSppConnectionLifecycle` 用 `AtomicReference` + 多个 volatile 标志（`YoCbtSppConnectionLifecycle.java:19-75`），但整体不是单一写线程。
- 影响：`open` 进行中与并发 `close/fail` 交错时，阶段可能从 `CONNECTED` 被 `FAILED` 覆盖或反之，对外快照出现“已连接后又 FAILED/断连”的抖动；此时 `terminal` CAS 只保证终止一次，不保证阶段一致。
- 建议：像 BLE 那样把 SPP 连接状态迁移收敛到 `runtime.state`（`open` 的阻塞 socket 操作放 io，状态写入回 state），或引入连接级串行执行器。

### CBT-04 | P2 | 诊断总线为进程级共享单例：BLE/CBT 报告互相污染，且去重集合无界
- 位置：`internal/common/diagnostics/BluetoothDiagnosticsBus.java:31`（`INSTANCE` 单例）、`:44`（`recordedTraceIds`）、`:121-125`（`clear()`）
- 证据：`YoCbtRuntime.diagnostics()` 返回 `BluetoothDiagnosticsBus.get()`（`YoCbtRuntime.java:112-115`）；`YoBleRuntime.diagnostics()` 同样返回该单例（`YoBleRuntime.java:145-148`）。
- 问题：(1) `ClassicClientImpl.exportDiagnosticReport()` 读取的是**全局**总线 `BluetoothDiagnosticsBus.get()`（`ClassicClientImpl.java:535-543`），因此 CBT 的诊断报告会包含 BLE 的失败/事件，反之亦然；(2) `recordedTraceIds` 只在 `clear()` 中清空，而 `clear()` 在仓库内**没有任何调用点**（`grep` 未命中生产调用），长时间运行下按唯一 traceId 无界增长；`timeline` 有 256 上限（`YoEventTimeline.java:30-35`）但去重集没有。
- 建议：诊断总线区分域（BLE/CBT）或按 runtime 归属；`recordedTraceIds` 增加容量/过期策略；`exportDiagnosticReport` 只取本 runtime 域数据。

### CBT-05 | P2 | 公开 `CbtErrorCode` 映射把大量内部错误码塌缩为 `SPP_CONNECT_FAILED`
- 位置：`internal/classic/ClassicClientImpl.java:913-1008`（`mapErrorCode`），`:1005-1006`（`default: return CbtErrorCode.SPP_CONNECT_FAILED;`）
- 证据：公开枚举 `CbtErrorCode` 只有 28 项（`model/classic/CbtErrorCode.java:6-35`），而内部 `YoCbtErrorCode` 有 100+ 项（`internal/classic/error/YoCbtErrorCode.java`）。未命中分支包括 `YO_CBT_SPP_CONNECTION_ID_NOT_FOUND`、`YO_CBT_SPP_CONNECTION_KEY_CONFLICT`、`YO_CBT_SPP_CHANNEL_IN_USE`、`YO_CBT_SPP_NOT_CONNECTED`、`YO_CBT_INTERNAL_ERROR`、`YO_CBT_TIMEOUT` 等，全部落到 `SPP_CONNECT_FAILED`。
- 影响：调用方无法区分“连接不存在”“通道被占用”“未连接”“超时”“连接失败”，难以做精确重试/提示。
- 建议：补齐公开错误码或采用“公开大类 + 原始 code 字符串”双轨；至少把 connection-id/not-connected/channel-in-use 明确暴露。

### CBT-06 | P2 | 类型/端点转换丢失信息、且 Profile 兜底映射错误
- 位置：
  - `ClassicClientImpl.java:731-761`（`toPublic(YoCbtProfileType)`）：`HFP/HSP/UNKNOWN → CbtProfileType.SPP`（`:755-760`）。`CbtProfileType` 本身无 HFP/HSP（`model/classic/CbtProfileType.java:6-18`），内部却有 `HFP/HSP`（`YoCbtProfileType.java:19-20`）。若某查询返回 HFP/HSP/UNKNOWN，会被误报成 **SPP**。
  - `ClassicClientImpl.java:1049-1057`（`toPublic(YoCbtSppEndpoint)`）：默认端点 `DEFAULT_SPP` 会因 `uuid==STANDARD_SPP_UUID` 被转成 `CbtSppEndpoint.uuid(...)`，丢失 `DEFAULT` 语义；`source`/`securityMode` 不参与公开模型（`CbtSppEndpoint` 无对应字段，`model/classic/CbtSppEndpoint.java:22-61`）。
- 影响：外部观察到错误的 profile 类型/端点类型，影响诊断与逻辑分支。
- 建议：`toPublic` 对 `HFP/HSP/UNKNOWN` 返回明确的“UNKNOWN/不支持”而非 SPP；端点转换保留 `DEFAULT` 判定（如 `isDefaultSpp()` → `CbtSppEndpoint.defaultSpp()`）。

### CBT-07 | P2 | 端点安全模式被硬编码为 SECURE；`channel` 端点默认必失败（契约陷阱）
- 位置：`ClassicClientImpl.java:593-609`（`toInternal(CbtSppEndpoint)`：UUID/CHANNEL 均 `YoCbtSppSecurityMode.SECURE`）；`YoCbtSppAndroidSocketFactory.java:56-66`（RFCOMM 通道连接受 `allowHiddenApiChannel` / `defaultChannelPolicy` 管控）；`internal/classic/api/.../YoCbtAndroidModuleGraph.java:270-272`（默认 `CbtConfig.allowHiddenApiSppChannel=false` → `YoCbtSppChannelPolicy.disabled()`）。
- 影响：公开 `CbtSppEndpoint.channel(n)` 在默认配置下**必然**因“RFCOMM channel connections are disabled by policy”失败（`YoCbtSppAndroidSocketFactory.java:57-59`），但 API 不提示；且公开模型无法选择 INSECURE/安全模式，无法连接未认证 SPP 服务。
- 建议：`CbtSppEndpoint` 暴露 securityMode/source（或明确文档说明恒 SECURE）；`channel` 端点在使用前给出显式能力校验与错误码。

### CBT-08 | P2 | 命令先发送后入队，入队失败产生“已发但未跟踪”的命令
- 位置：`YoCbtCommandSession.java:57-87`：`:72 sender.send(bytes)` 在 `:78 queue.enqueue(...)` 之前；`retrySend` 同样先 `sender.send` `:173` 再入队 `:174`。
- 影响：若发送成功而入队失败（并发占满队列），该命令的响应到达时无 pending 记录，`dispatchPacket` 只会报 `YO_CBT_RESPONSE_NOT_MATCHED`（`:122-130`），且原始命令结果丢失。
- 建议：先入队成功再发送，或发送失败时回滚入队。

### CBT-09 | P2 | 重试节流被定义但未使用；若干超时/队列 API 为死代码
- 位置/证据：
  - `YoCbtRetryPolicy.retryDelayMillis` 定义了 `getRetryDelayMillis()`（`protocol/retry/YoCbtRetryPolicy.java:11,22-24`），但 `retrySend` 立即重发，**未使用延迟**（`YoCbtCommandSession.java:169-186`）。
  - `YoCbtCommandQueue.pollTimeout()`（`protocol/command/YoCbtCommandQueue.java:67-79`）及 `PendingEntry.timeoutCallback`（`:97-105`）仅测试引用（`grep pollTimeout` 生产无调用）。
  - `YoCbtCommandTimeout.isExpired()/remainingMillis()/markExpired()`（`protocol/timeout/YoCbtCommandTimeout.java:34-45`）生产未使用。
- 建议：删除或接入；重试节流与配置对齐（否则“重试间隔”配置无效）。

### CBT-10 | P2 | 线程断言 API 从未被调用，单线程 state 契约未强制
- 位置：`internal/classic/runtime/YoCbtRuntime.java:181-237`（`checkStateThread/checkIoThread/checkCallbackThread`）
- 证据：`grep` 全 classic 包仅命中定义处，无调用；且 §CBT-03 显示 SPP 并未使用 state 线程。
- 建议：要么在领域组件入口真正断言，要么删除以免误导；与 §CBT-03 一并整改线程模型。

### CBT-11 | P2 | 生命周期/状态字段跨线程读写未加可见性保证
- 位置/证据：`YoCbtAndroidClient.java:88`（`private boolean released;` 非 volatile，`discover/getAclState/...` 在调用方线程读，`release()` 写）；`YoCbtDiscoveryController.java:155-170`（`cancelForConnect()` 在调用方线程直接改 `discoveryPlatform`、`state.set(IDLE)`、`clearReceiverListener()`，而发现回调在 state 线程 `YoCbtDiscoveryReceiverAdapter.java:64` 执行）。
- 影响：极端时序下 `released` 可见性滞后；发现状态可能短时不一致（多为 benign，因 `activeSession.getAndSet(null)` 与 listener 置 null 提供兜底）。
- 建议：`released` 改 `volatile`/`AtomicBoolean`；`cancelForConnect` 收敛到 `runtime.state`。

### CBT-12 | P2 | 静默吞异常清单（分类见第 5 节）
- `catch (Throwable ignored)`: `spp/transport/YoCbtSppTransport.java:55`、`protocol/codec/YoCbtFramedPacketCodec.java:47`、`internal/android/spp/YoCbtAndroidSppSocket.java:71`、`profiles/extensions/YoCbtAndroidTransientProfileProxyExtension.java:48`、`internal/android/YoCbtAndroidDeviceMapper.java:31,39,66`、`internal/android/profiles/YoCbtAndroidConnectedDeviceQuery.java:166,262`、`internal/android/YoCbtAndroidA2dpProfilePort.java:88`、`receiver/YoCbtSdpReceiverAdapter.java:128`。
- `catch (Exception e)`（记录到诊断总线，非吞没）：`YoCbtAndroidProfileStateQuerier.java:65,137,221,259` 等。
- 详见第 5 节“必要兼容 vs 冗余补丁”的处置建议。

### CBT-13 | P2 | hiddenapi/反射兼容：必要兼容，但反射恒失败风险需显式降级
- 位置：`YoCbtHiddenApiBondManager.java:38`（`device.getClass().getMethod(methodName)`）、`YoCbtHiddenApiRfcommConnector.java:40`（`getMethod(methodName, int.class)`）、`YoCbtAndroidSppSocket.java:62-75`（反射私有 `mPort`）。
- 判断：**必要兼容**——`removeBond/cancelBondProcess/channel socket/mPort 反推通道` 均无公开 API，只能反射，且受 `allowHiddenApiBondManagement` / `allowHiddenApiSppChannel` 显式开关保护（`YoCbtAndroidBondOperationPlatform.java:81-86,111-116`；`YoCbtSppAndroidSocketFactory.java:56-66`）。`mPort` 失败时静默返回 -1 是合理降级。
- 风险：现代 ROM 上 `BluetoothDevice` 为 final、方法不在 `getMethod` 可见集合内，反射可能稳定抛 `NoSuchMethodException`；届时 `removeBond` 将恒 `BOND_REMOVE_FAILED`。
- 建议：把“ROM 不支持 hidden API”作为显式能力探测结果并在返回错误中区分（`YoCbtErrorCode.YO_CBT_SPP_CHANNEL_UNSUPPORTED_ON_ROM` 已存在但未被具体使用）。

### CBT-14 | P2 | 整个 CBT 子系统当前无生产消费者
- 证据：
  - `grep` 全仓 `YoCbtClient|new ClassicClientImpl|classic()` 仅命中 `lib_bluetooth` 主源码与 `src/androidTest`，**无 app/biz 生产引用**。
  - `biz_ggec_hs` 对 `lib_bluetooth` 的引用只有 `provider/lib_bluetooth/YoBleCommandLink.java`（纯 BLE、`import ...model.ble.*`）与 `HsScanSnapshots.java`（`BleScanRecord`），注释明确“这是 biz_ggec_hs 里唯一允许引用 lib_bluetooth 的类型”（`YoBleCommandLink.java:27-31`）。
  - `biz_ggec_hs` 中 `classicAddress()`（如 `Hs01Identity.java:52-53`、`Hs02Identity.java:61-62`）与 `ClassicBtAddressCommand`（`internal/protocol/devicecmd/ClassicBtAddressCommand.java:12-15`）只是“经 BLE 命令链路获取耳机经典 MAC 地址”的业务模型/命令，**不是** CBT SPP 客户端。
  - `app` 生产代码也只引用 BLE（`HsDevicePort.java:12-24`、`HsBroadcastScanMapper.java:16-17`）。
- 影响：CBT 约 190 个主源码文件目前处于“已实现、未接线”状态，是显著的维护/认知负担与潜在缺陷温床。
- 建议：明确 CBT 的接入计划（未来 SPP 协议消费者是谁）；在接入前对其做针对性测试，否则考虑冻结/裁剪。

### CBT-15 | P2 | 发现停止的无会话场景复用了“已在运行”错误码
- 位置：`internal/classic/discovery/YoCbtDiscoveryController.java:126-133`（无 active session 时返回 `YO_CBT_DISCOVERY_ALREADY_RUNNING`，语义应为“无进行中的发现”）。
- 建议：补 `YO_CBT_DISCOVERY_NOT_RUNNING` 或复用更贴切错误码。

### 3.x 能力面契约矩阵（抽检结论）
| 能力 | 公开方法 | 实现链 | 契约状态 |
|---|---|---|---|
| 发现 | `discover` / `stopDiscovery` | ClassicClientImpl:120-152 → Controller | 完成；`requireName` 生效（`YoCbtDiscoveryConfig.toFilter` `:31-34`） |
| 配对 | `createBond/cancelBondProcess/removeBond` | → BondOperationController/Platform | 完成；cancel/remove 依赖 hidden API（CBT-13） |
| ACL | `observeAclLinkState/getAclState/getAclConnectedDevices/queryConnectedDevices` | → AclLinkRepository/ConnectedDeviceQuery | 完成 |
| SDP | `discoverServices` | → ServiceDiscoveryClient | 完成 |
| Profile | `prepareProfileProxy/getProfileAggregateState/getDeviceProfileState/getDeviceProfileSnapshot/getConnectedProfiles/observeProfileState×2` | → ProfileProxyManager/StateQuerier/Repository | 完成；类型映射有瑕疵（CBT-06） |
| SPP | `connectSpp/disconnectSpp/.../send/sendCommand/observeCommandResult/observeSppState/getActiveSppConnections/getSppConnections` | → SppConnectionManager/Connection/Writer/Reader | 收发闭合但帧化/匹配有缺（CBT-02） |
| 诊断 | `exportDiagnosticReport` | `BluetoothDiagnosticsBus.get()` | 完成但跨域污染（CBT-04） |

---

## 4. CBT ↔ BLE 解耦结论（带证据）

**结论：CBT 与 BLE 在代码依赖、运行时、线程执行器、事件总线上彼此独立；唯一的共享可变单例是进程级诊断总线（诊断汇聚，非控制通道），不构成运行耦合。**

1. **无交叉 import（双向 0 命中）**
   - `grep 'import com.yovo.lib_bluetooth.internal.ble'` 在 `internal/classic/**` → **No matches**。
   - `grep 'import com.yovo.lib_bluetooth.internal.classic'` 在 `internal/ble/**` → **No matches**。
   - 证据来源：本轮两次 `grep` 结果（classic 目录、ble 目录）。
2. **独立 Runtime 实例**
   - CBT：`YoCbtAndroidModuleGraph.create` 内 `new YoCbtRuntime(clientConfig.getRuntimeConfig())`（`YoCbtAndroidModuleGraph.java:191`）。
   - BLE：`YoBleRuntime` 注释自述“BLE 模块唯一的线程模型”（`internal/ble/runtime/YoBleRuntime.java:23-39`），由 BLE 自己的模块图构造（`BleClientImpl`/`YoBleAndroidModuleGraph`）。
3. **独立执行器 / 线程命名**
   - CBT：`YoExecutors.single(stateThreadName)`、`fixed("yo-cbt-io", ...)`、`scheduled("yo-cbt-scheduler")`、`single("yo-cbt-callback")`（`YoCbtRuntime.java:52-64`）。
   - BLE：`newSingleThreadExecutor(stateThreadName)`、`newFixedThreadPool(..., numbered("yo-ble-io"))`、`newSingleThreadScheduledExecutor(... "yo-ble-scheduler")`、`"yo-ble-callback"`（`YoBleRuntime.java:72-98,206-209`）。
   - 命名前缀 `yo-cbt-*` 与 `yo-ble-*` 完全隔离，互不共享线程池。
4. **事件总线按 runtime 独立实例**
   - CBT：`private final BluetoothEventBus eventBus = new BluetoothEventBus();`（`YoCbtRuntime.java:38`），`eventBus()` `:108-110`。
   - BLE：`private final BluetoothEventBus eventBus = new BluetoothEventBus();`（`YoBleRuntime.java:61`），`eventBus()` `:141-143`。
   - 即 `BluetoothEventBus` 虽是 common 类，但**每个 runtime 各持一份**，且随 runtime `release()` 清空（`YoCbtRuntime.java:142`、`YoBleRuntime.java:195`）。
5. **唯一的共享态：`BluetoothDiagnosticsBus` 进程单例**
   - `private static final BluetoothDiagnosticsBus INSTANCE`（`BluetoothDiagnosticsBus.java:31`）；两侧 `diagnostics()` 均返回 `get()`（`YoCbtRuntime.java:112-115`、`YoBleRuntime.java:145-148`）。
   - 性质：只做“记录/时间线/观察者”汇聚，不含 BLE/CBT 的命令/连接状态，因此不产生行为耦合；但带来 CBT-04 的“报告跨域污染 + 去重集无界”问题，需注意。

**关于 biz_ggec_hs 的关系（任务点 5）：**
- Classic **确实提供** SPP 能力面（`YoCbtClient.connectSpp/send/sendCommand/observeCommandResult/observeSppState/observeSppState`，`api/YoCbtClient.java:101-135`），可作为“其他协议”的传输层。
- 但 **biz_ggec_hs 当前未使用 Classic**：其唯一 `lib_bluetooth` 入口是 `provider/lib_bluetooth/YoBleCommandLink`（BLE），且注释限制“唯一允许引用 lib_bluetooth 的类型”（`YoBleCommandLink.java:27-31`）。若未来协议需要走 SPP，**不能**让 biz 直接 `import api.YoCbtClient`，需在 `provider/lib_bluetooth` 增补一个类似 `YoCbtCommandLink` 的适配器，保持“biz 只经 provider”约束。

---

## 5. 冗余 / 防御性代码清单

### 5.1 必要兼容（建议保留）
| 位置 | 说明 | 处置 |
|---|---|---|
| `YoCbtAndroidSppSocket.java:60-75` 反射私有 `mPort` | 公开 API 无法取协商 RFCOMM 通道；失败回 -1 合理 | 保留；可加一次性能力探测缓存 |
| `YoCbtHiddenApiRfcommConnector.java:25-54`、`YoCbtHiddenApiBondManager.java:21-47` | `removeBond/cancelBondProcess/channel socket` 无公开 API，且被配置开关保护 | 保留；补“ROM 不支持”显式错误码（见 CBT-13） |
| `YoCbtAndroidDeviceMapper.java:29-42,60-68` `catch(Throwable)` | `getAddress()/getName()/getType()` 在缺权限/异常 ROM 上会抛 `SecurityException`，兜底为空值可接受 | 保留 |
| `YoCbtAndroidConnectedDeviceQuery.java:163-168` `catch(Throwable)` 包 `getConnectedDevices` | 单个 profile 代理异常不应中断整体聚合 | 保留（可仅记录到诊断总线） |

### 5.2 冗余 / 建议清理或接线
| 位置 | 问题 | 处置建议 |
|---|---|---|
| `YoCbtCommandQueue.pollTimeout()` `:67-79` + `PendingEntry.timeoutCallback` `:97-105` | 生产未调用，仅测试引用；超时实际由 `scheduler` 直接驱动 | 删除或改为唯一超时通道（择一） |
| `YoCbtCommandTimeout.isExpired/remainingMillis/markExpired` `:34-45` | 生产未使用 | 删除 |
| `YoCbtRetryPolicy.retryDelayMillis` `:11,22-24` | 重试未使用延迟 | 接入 `retrySend` 或删除 |
| `YoCbtRuntime.checkStateThread/checkIoThread/checkCallbackThread` `:181-237` | 从未调用 | 接入领域组件入口或删除（CBT-10） |
| `YoCbtFramedPacketCodec` / `YoCbtPacketFramer`（生产未接入） | 帧化能力存在但命令会话用 raw codec | 接入生产（CBT-02）或判定为未用能力 |
| `YoCbtAndroidTransientProfileProxyExtension.java:44-51` `catch(Throwable)→success(false)` | 静默把异常当作“未连接”，可能掩盖真实错误 | 至少 `recordException` 到诊断总线 |
| `YoCbtSppTransport.java:45-56` `closeQuietly` 里的 `InputStream/OutputStream instanceof` 分支 | 实际上传入的都是 `Closeable` 流，后两个分支冗余 | 简化 |
| `YoCbtSppWriteQueue.drainTo/poll()`（`spp/io/YoCbtSppWriteQueue.java:70-109`） | `drainTo`/无参 `poll` 生产未使用（写线程用 `poll(timeout)`） | 删除未用方法 |
| `YoCbtAndroidProfileProxyManager.releaseProxy/isProxyReady` 等 | 需核对是否被 `prepareProfileProxy` 之外调用（本轮未见 releaseProxy 调用点） | 复核后清理 |

> 说明：`docs/audit/dead_types_round1.txt` 已列出上一轮清理的 33 个已删除类型（如 `YoCbtPacketDecoder/YoCbtPacketEncoder/YoCbtRetryDecision/YoCbtTimeoutScheduler` 等），当前 `glob` 中已不存在，故本轮不重复。

---

## 6. 未知项

1. **运行期行为未观测**：本轮为纯静态只读审计，未在真机/模拟器执行，CBT-01/02/03 的并发竞态为静态推断（有代码证据，未见运行时复现）。
2. **hidden API 在各 ROM 的实际可用性未知**：`removeBond/cancelBondProcess/channel socket/mPort` 反射是否成功取决于 ROM，未做设备验证（CBT-13）。
3. **命令/SDP 的市场真实报文格式未知**：`sendCommand` 的响应匹配、帧边界是否与真实耳机协议一致，仓库内无消费者可验证（CBT-02、CBT-14）。
4. **BLE 侧模块图装配未逐行取证**：仅据 `YoBleRuntime` 自述与执行器命名证明独立实例与线程，未展开 `BleClientImpl/YoBleAndroidModuleGraph` 全部构造。
5. **Profile 扩展（AVRCP/MAP/OPP/PAN/PBAP/HID/LE_AUDIO/HEARING_AID）逐个实现未全部精读**：仅核对了 `YoCbtProfileType` 语义与扩展注册骨架（`YoCbtAndroidProfileExtensionBootstrap`），其具体平台适配仍未逐文件取证。
6. **`YoCbtSppConnectionRegistry` 的 key 冲突/容量上限语义**：本轮只核对 `hasActiveKey/idsByDevice` 调用，未确认是否存在连接数上限（`YO_CBT_SPP_CONNECTION_LIMIT_EXCEEDED` 定义了但未见触发点）。

---

### 附：本轮取证的文件（代表）
`api/YoCbtClient.java`；`internal/classic/ClassicClientImpl.java`；`internal/classic/CbtPublicSppStateGate.java`；`internal/classic/runtime/{YoCbtRuntime,YoCbtRuntimeConfig,YoCbtRuntimeScope,YoCbtEvents,YoCbtReleaseCoordinator}.java`；`internal/classic/internal/android/{YoCbtAndroidClient,YoCbtAndroidModuleGraph,YoCbtSppAndroidSocketFactory,YoCbtAndroidDeviceMapper,YoCbtSpp...}.java`；`internal/classic/internal/android/hiddenapi/*`；`internal/classic/internal/android/spp/*`；`internal/classic/internal/android/profiles/**`；`internal/classic/discovery/*`；`internal/classic/pairing/*`；`internal/classic/protocol/{command,codec,packet,matcher,retry,timeout}/*`；`internal/classic/spp/{connection,io,transport,policy,endpoint,registry}/*`；`internal/classic/profiles/core/YoCbtProfileType.java`；`internal/common/diagnostics/{BluetoothDiagnosticsBus,BluetoothEventBus,YoEventTimeline}.java`；`internal/ble/runtime/YoBleRuntime.java`；`model/classic/*`；`biz_ggec_hs/provider/lib_bluetooth/YoBleCommandLink.java`。
