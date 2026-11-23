# 录音文件命名规范（权威）

> 本文是录音文件命名的**唯一权威定义**。仓内凡是"把录音写到文件"的地方，文件名一律按本文生成；
> 与本文冲突的现有写法按本文改正，不在业务层再各写一套。
> 关联文档：`product-taxonomy.md`（产品型号 `modelId` 来源）、`stream-record-refactor.md`（随录随传链路）。

## 1. 目标

统一录音文件名为「**产品型号 + 录音类型 + 时间**」，让本地录音目录无需打开文件即可从文件名判断：

- 是哪台/哪类型号的设备录的（产品型号）；
- 是什么录音（实时随录 / 设备文件同步 / 音视频旁路录制）；
- 什么时候录的（精确到秒）。

## 2. 录音子系统梳理（现状）

仓内共有 3 个模块会产出录音音频文件，命名目前各不相同：

| 模块 | 能力 | 产出类 | 现状命名 | 当前实现位置 |
|------|------|--------|----------|--------------|
| `biz_ggec_hs` | 随录随传（实时录音） | `.pcm` | `live_<yyyyMMdd_HHmmss>.pcm` | `internal/record/HsRecordPathResolver#generateDefaultStreamFile` |
| `biz_ggec_hs` | 设备录音文件同步到手机 | `.pcm` | `sync_<fileId>_<yyyyMMdd_HHmmss>.pcm` | `internal/record/HsRecordPathResolver#generateDefaultTransferFile` |
| `biz_ggec_wy` | 随录随传（实时录音） | `.pcm` | `WY_stream_<yyyyMMdd_HHmmss>.pcm` | `internal/record/WyRecordPathResolver#generateDefaultStreamFile` |
| `biz_ggec_record` | 音视频旁路录制（预览同时封装） | `.mp4` | `ble_av_<epochMillis>.mp4` | `internal/session/YoRecordClientImpl#startRecording` |

通用落盘规则集中在 `lib_ggec_audio.record.StreamRecordTargets`（有自定义文件名则用自定义，否则用产品默认时间戳文件）。
「默认文件名」由各模块的 PathResolver 产出；本次把命名规则收敛到 `lib_ggec_audio.record.RecordFileName` 一处。

### 2.1 现状问题

1. **前缀不统一**：`live_` / `sync_` / `WY_stream_` / `ble_av_` 四套词，看不出设备型号。
2. **时间格式不统一**：`yyyyMMdd_HHmmss` 与 `epochMillis` 混用，`ble_av_1789...mp4` 不可读。
3. **型号缺位**：HS 的 `live_`、`sync_` 完全不含型号，HS01 与 HS02 混在同一目录无法区分。
4. **类型词表不统一**：`live` / `sync` / `stream` / `av` 语义重叠，缺少受控词表。

## 3. 命名标准

### 3.1 语法

```
<产品型号>_<录音类型>[_<来源标识>]_<时间戳>.<扩展名>
```

- **产品型号**：连接设备的 `modelId`（如 `HS01`、`HS02`、`WY01`；`biz_ggec_record` 取设备广播名/型号标识）。
  取值来自产品目录/扫描结果，**不拼接、不臆造**；连接前或缺省时回落 `UNKNOWN`。
- **录音类型**：受控词表，见 3.2。
- **来源标识**（可选）：仅「设备文件同步」使用，填设备侧文件序号 `fileId`，用于区分同一个录音设备里不同文件。
  其它类型省略该段。
- **时间戳**：`yyyyMMdd_HHmmss`（本地时区、24 小时制），精确到秒。
- **扩展名**：小写、不带点（`pcm` / `mp4`）。

### 3.2 录音类型受控词表

| 类型 token | 含义 | 典型模块 |
|-----------|------|----------|
| `stream` | 随录随传 / 实时录音（边收边落盘） | `biz_ggec_hs`、`biz_ggec_wy` |
| `sync` | 设备内录音文件同步到手机本地 | `biz_ggec_hs` |
| `av` | 音视频旁路录制（预览同时封装 MP4） | `biz_ggec_record` |

> 新增录音形态时，只允许在此表追加 token，禁止在业务代码里自造前缀词。

### 3.3 示例

| 场景 | 文件名 |
|------|--------|
| HS01 实时录音 | `HS01_stream_20261119_163237.pcm` |
| HS01 同步设备内 3 号文件 | `HS01_sync_3_20261119_164500.pcm` |
| HS02 实时录音 | `HS02_stream_20261119_170102.pcm` |
| WY01 实时录音 | `WY01_stream_20261119_163237.pcm` |
| 旁路音视频录制（设备标识 `ABC01`） | `ABC01_av_20261119_165000.mp4` |
| 连接前/设备标识缺失 | `UNKNOWN_stream_20261119_163237.pcm` |

### 3.4 字符与长度

- 各段仅保留 `A-Z a-z 0-9 -`；其余字符（空格、`:`、`/`、中文等）折叠为单个 `-`，首尾 `-` 去除。
- 长度上限：产品型号 32、录音类型 16、来源标识 16（超出截断）。
- 各段为空时：产品型号回落 `UNKNOWN`，类型回落 `rec`，来源标识整段省略。

## 4. 各产出点改造对照

| 产出点 | 改造后调用 |
|--------|-----------|
| `HsRecordPathResolver#generateDefaultStreamFile` | `RecordFileName.build(型号, TYPE_STREAM, now, "pcm")` |
| `HsRecordPathResolver#generateDefaultTransferFile(fileId)` | `RecordFileName.build(型号, TYPE_SYNC, fileId, now, "pcm")` |
| `WyRecordPathResolver#generateDefaultStreamFile` | `RecordFileName.build(型号, TYPE_STREAM, now, "pcm")` |
| `YoRecordClientImpl#startRecording` | `RecordFileName.build(设备标识, TYPE_AV, now, "mp4")` |

**型号注入方式（不破坏分层）**：

- `HsRecordPathResolver` / `WyRecordPathResolver` 增加构造参数 `Supplier<String> deviceCodeSupplier`，
  在组合根 `HsGraph` / `WyGraph` 里绑定 `() -> session.peer().modelId()`，即取当前连接型号。
  （保留单参构造，默认返回空串 → 回落 `UNKNOWN`，兼容单测与离线场景。）
- `biz_ggec_record` 无产品目录概念，取扫描设备的广播名作为设备标识：
  `YoRecordClient.connect(mac, deviceCode)` 新增带标识的重载，`RecordMainActivity` 传 `device.name()`。

## 5. 边界与兼容

- **分层**：命名格式化在 `lib_ggec_audio.record.RecordFileName`，只做字符串拼接，不出现任何具体产品类型；
  产品型号以不透明字符串传入 → `lib_ggec_audio` 边界门禁（禁止 `modelId/projectId/deviceType/HS0/WY0` 关键字）不受影响。
- **自定义文件名**：`StreamRecordTargets` 的「自定义名优先」规则不变；本规范只约束**默认名**。
- **时长/列表**：文件仍以 `.pcm`/`.mp4` 结尾，`listLocalSavedFiles` 的后缀过滤与时长估算逻辑不受影响。
- **唯一性**：同一类型同一秒内不并发录音（会话串行），故秒级时间戳已足够；同步文件额外带 `fileId` 天然区分。

## 6. 验收

1. 单测：`RecordFileName` 覆盖拼接、时间格式、字符清洗、缺省回落、来源标识可选。
2. 单测：`WyRecordPathResolver` 以型号 `WY01` 生成的文件名匹配 `WY01_stream_\d{8}_\d{6}\.pcm`。
3. 真机：HS01/HS02 随录、WY01 随录、设备文件同步、旁路录制各产出一个文件，文件名符合第 3 节。
4. 门禁：`checkLibGgecAudioBoundary`、`checkBizGgecHsBoundary`、`checkBizGgecWyBoundary`、`checkBizGgecRecordBoundary` 全通过。

## 7. 代码改动清单

- 新增 `lib_ggec_audio/src/main/java/com/yovo/lib_ggec_audio/record/RecordFileName.java`
- 新增 `lib_ggec_audio/src/test/java/com/yovo/lib_ggec_audio/record/RecordFileNameTest.java`
- 改 `biz_ggec_hs/.../internal/record/HsRecordPathResolver.java`、`internal/center/HsGraph.java`
- 改 `biz_ggec_wy/.../internal/record/WyRecordPathResolver.java`、`internal/center/WyGraph.java`
- 改 `biz_ggec_record/.../api/YoRecordClient.java`、`internal/session/YoRecordClientImpl.java`、`build.gradle`
- 改 `app/.../record/ui/RecordMainActivity.java`（连接时传设备名作标识）
