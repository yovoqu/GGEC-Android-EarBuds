# BleVideoPlayer

配合 Ambiq BLE 固件的 Android 实时音视频播放与端侧语音转写 App（Kotlin）。

对端设备通过自定义 128-bit GATT 服务，以独立 Notify 特征推送 **H.264/H.265
视频裸流**和 **PCM/AAC/Opus 音频**。视频按起始码恢复 NAL 后硬解到
`TextureView`；音频解码后可播放，并统一转成 16 kHz 单声道 PCM，由随 APK
打包的 sherpa-onnx 中英双语模型实时转成文字。纯音频、纯视频和音视频混合
均可使用，另有不依赖 BLE 的本地音频转写页面。

## 如何打开与编译

本工程**不包含 Gradle Wrapper**（未提交 `gradle-wrapper.jar`），请直接用
Android Studio 打开，首次同步会自动下载 AGP 与对应版本的 Gradle：

1. 安装 **Android Studio Ladybug (2024.2) 或更新版本**。
2. 确认 Gradle JDK 为 **17**：
   `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`
   选择 17（AGP 8.5.x 强制要求 JDK 17）。
3. `File → Open`，选择本目录 `android/BleVideoPlayer/`（即包含
   `settings.gradle.kts` 的目录）。
4. 首次 Gradle Sync 会自动下载 AGP 8.5.2、Kotlin 插件 2.0.20 及 Gradle 8.7+，
   需保持网络畅通。
5. Sync 完成后：`Build → Make Project`，或连接手机后 `Run → Run 'app'`。

命令行构建（可选，需本机已安装 Gradle 8.7+ 与 Android SDK）：

```sh
gradle assembleDebug
```

## 安装与运行

- 手机要求：Android 8.0+（minSdk 26），支持 BLE 与 H.264/H.265 硬解。
- 打开 App 后先授予权限，点 **扫描**，在列表中选择设备（`dabaicai--test`
  会自动排在最前，其余设备也可手动选择），点 **连接**。
- 顶部状态栏每秒刷新：接收 kbps、解码 fps、队列水位（KB）、丢弃块数。
- **H.264 / H.265 按钮**切换解码 MIME（会重建解码器），请与设备端实际编码
  格式保持一致。
- 底部输入框 + **发送** 按钮：把 ASCII 文本写入 RX 特征。

### 所需权限

| 权限 | 说明 |
|---|---|
| `BLUETOOTH_SCAN`（`neverForLocation`） | Android 12+ 扫描 |
| `BLUETOOTH_CONNECT` | Android 12+ 连接 |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`（maxSdkVersion 30） | Android 11 及以下扫描所需 |

## 设备名 / UUID 对照表

| 项目 | 值 |
|---|---|
| 广播名 | `dabaicai--test` |
| Service UUID | `00002760-08c2-11e1-9073-0e8ac72e5450` |
| 视频 TX 特征（Notify，设备→手机） | `00002760-08c2-11e1-9073-0e8ac72e5402` |
| 音频 TX 特征（Notify，设备→手机，默认值，界面可改） | `00002760-08c2-11e1-9073-0e8ac72e5403` |
| RX 特征（Write，手机→设备，配置下发） | `00002760-08c2-11e1-9073-0e8ac72e5401` |

## 链路参数与带宽说明

- 连接后 App 依次协商：`CONNECTION_PRIORITY_HIGH` → MTU 517 → 收发均 2M PHY
  → 服务发现 → 订阅 TX 通知（兼容 API 33 前后的 `writeDescriptor` /
  `onCharacteristicChanged` 签名）。
- 链路吞吐约 **1.2 Mbps**（固件侧已请求 DLE 251；ATT MTU 以实际协商结果为准）。
  建议设备端优先使用 **H.265 低码率**编码；若码率长期超过链路能力，App 输入
  队列（4 MB）溢出时会丢弃最旧数据并在状态栏计数，表现为画面花屏/卡顿。

## 蓝牙芯片与 App 视频数据交互协议

本节描述当前固件和 App **已经实现**的交互方式。视频通道是连续字节流，不是
“一个 Notify 包对应一帧”的消息协议，也没有自定义视频帧头。

### 1. GATT 角色与通道

蓝牙芯片是 GATT Server / BLE Peripheral，App 是 GATT Client / BLE Central。

| 通道 | 方向 | 传输方式 | 内容 |
|---|---|---|---|
| TX 特征 `...5402` | 芯片 → App | Notify | H.264/H.265 Annex B 裸码流片段 |
| RX 特征 `...5401` | App → 芯片 | Write | ASCII 文本；视频配置命令尚未定义 |

App 还需向 TX 特征的 CCCD（UUID `00002902-0000-1000-8000-00805f9b34fb`）
写入 `0x0001`（线上字节 `01 00`）以启用 Notify。视频数据只走 TX，不存在通过
RX 返回的逐包 ACK。

### 2. 连接、协商与开始发送

1. App 连接设备并请求高连接优先级。
2. App 请求 MTU 517，随后请求收发均使用 2M PHY；固件另行请求 DLE 251 和
   15～30 ms 的连接间隔。以上均为请求，实际参数以双方协商结果为准。
3. App 发现自定义服务和特征，并订阅 TX Notify。
4. CCCD 写入成功后，App 才允许使用 RX 配置通道；固件在“已连接且 TX Notify
   已订阅”后开始转发已有视频数据。

当前协议没有 `START`、`STOP` 或暂停视频命令。连接但未订阅、取消订阅或断开后，
固件不会向 App 发送视频，并会清空尚未进入 BLE 发送缓冲的上行缓存数据。

MTU 517 只是 App 的请求值，不能当作固定值使用。单个 Notify 的视频载荷上限为：

```text
max_notify_payload = negotiated_ATT_MTU - 3
```

例如 ATT MTU 为 23 时载荷最多 20 字节，ATT MTU 为 247 时最多 244 字节。
最后一块可以小于该上限；接收端不得按固定包长解析码流。

### 3. 视频载荷格式

- 编码格式为 **H.264 或 H.265 Annex B elementary stream**。App 同时识别
  `00 00 00 01` 和 `00 00 01` 两种起始码。
- Notify 载荷就是原始码流字节。SPI 链路的帧头、序号和校验字段不会放进 BLE
  视频数据。
- 没有私有帧头、帧长度、帧序号、时间戳、CRC、RTP 头或容器封装；也没有单独
  的 SPS/PPS 配置消息。
- H.264 码流应包含 SPS/PPS 和 IDR；H.265 码流应包含 VPS/SPS/PPS 和 IDR。
  为支持中途接入、解码器重建及丢包后的恢复，发送端应在每个关键帧前或按合理
  周期重复参数集，至少应保证新会话的首个关键帧之前带有完整参数集。
- 分辨率、帧率和编码参数由码流中的参数集决定，App 不通过 RX 预设分辨率。
  **编码格式不会自动协商**：App 的 H.264/H.265 按钮只重建本地对应 MIME 的
  解码器，必须与芯片实际发送的编码格式一致，不会向芯片下发切换命令。

### 4. Notify 分包与 App 重组

固件按当前可用载荷长度依次切分连续码流，一包可以包含任意字节片段：

- 一包可以只包含一个 NAL 的一部分，也可以包含多个 NAL；
- NAL、图像帧、参数集甚至 3/4 字节起始码都可以跨两个或多个 Notify；
- 不要求 Notify 从起始码开始，也不要求在图像帧或 NAL 边界结束；
- 不传输视频帧边界和呈现时间戳，App 使用提交解码器时的本机时间进行直播渲染。

App 将所有 TX Notify 的载荷**按到达顺序直接拼接**，缓存跨包残尾，再按起始码
恢复完整 NAL。恢复出的完整 NAL 才进入解码队列并提交给 `MediaCodec`。因此发送端
无需为 BLE 分包增加任何私有头或边界标记，接收端也绝不能把每次 Notify 回调直接
当作一帧视频。

例如 ATT MTU 为 247 时，码流字节 `B0...B999` 可以按以下方式传输：

```text
Notify #1: B0...B243     （244 字节）
Notify #2: B244...B487   （244 字节）
Notify #3: B488...B731   （244 字节）
Notify #4: B732...B975   （244 字节）
Notify #5: B976...B999   （24 字节）
```

上述边界仅用于说明“任意切片”，不表示 NAL 或图像帧边界。

### 5. 发送确认、可靠性与溢出处理

- 固件同一时间只发送一个 Notify，收到协议栈的 `ATTS_HANDLE_VALUE_CNF` 后再
  发送下一块；这只是发送流水控制，不是 App 的视频数据确认。
- 当前视频层没有序号、应用层 CRC、丢包检测、重传或 App → 芯片的码率反馈。
  BLE 链路或缓冲区造成的数据丢失无法靠视频协议定位，可能表现为花屏、卡顿或
  等待下一个关键帧。
- 固件使用 32 KB 上行环形缓冲和 16 KB BLE 发送环形缓冲；BLE 未连接或未订阅时
  丢弃上行缓存，BLE 发送缓冲溢出时当前实现会主动关闭连接并统计丢弃字节。
- App 的解码输入队列上限为 4 MB。队列满时丢弃最旧的完整 NAL，不阻塞 BLE
  回调，并在状态栏累计丢弃数。持续丢块说明输入码率或解码速度长期不匹配，
  应优先降低设备端码率，而不是增大队列掩盖问题。
- App 没有向芯片发送反压或丢弃通知的控制协议；链路吞吐约 1.2 Mbps 是当前
  参考值，不是协议保证值，编码码率应留出协议开销和无线波动余量。

### 6. RX 下行配置通道：当前状态

- **App 侧已实现**：底部输入框内容由 `BleManager.sendText()` 转成 US-ASCII
  原样写入 RX 特征，不自动追加 `\0`、`\r` 或 `\n`，一次点击对应一次写入。
  单次有效载荷应不超过当前 `ATT_MTU - 3`。
- **固件侧待开发**：分辨率、帧率、码率、编码格式和启停等视频配置命令及其响应
  格式均未定义；当前发送普通配置文本不会改变视频参数，也没有配置结果响应。
- RX 仍复用 throughput 示例的接收回调，不能把其中遗留的测试命令当作视频协议。
  在正式定义命令字、长度、版本和响应前，不应自行约定或依赖二进制控制命令。
- 固件当前将 RX 声明为 **Write Without Response**，而 App 当前使用
  `WRITE_TYPE_DEFAULT`（Write With Response）；后续实现配置协议时应统一两端
  写入方式，不能据此假设下行命令具备应用层可靠确认。

## 音频与音视频混合

- 视频仍使用原 TX 裸流；音频使用独立 TX，App 串行订阅两个特征，按 UUID 分发。
  服务中只有一个 TX 也能工作，音视频同时到达时互不改写对方码流。
- 音频格式在主界面选择，参数会保存：

| 格式 | 固件发送约束 | App 处理 |
|---|---|---|
| PCM | 16-bit 小端；采样率、单/双声道与界面一致；Notify 可任意切片 | 跨块保留奇数残字节，下混并重采样 |
| AAC | ADTS 流；Notify 可任意切片 | 恢复完整 ADTS 帧后用 `MediaCodec` 解码 |
| Opus | **一次 Notify 恰好一个完整 Opus 包**，不跨包、不粘包 | 按界面声道配置 `MediaCodec`，输出 48 kHz PCM |

音频可静音播放。BLE 回调只写入有界队列，解码、播放、重采样与识别均在后台；
队列溢出会丢最旧数据并计数。修改音频编码或参数会重建音频管线，修改音频
UUID 后需重新连接。

## 端侧实时语音转文字

- 引擎：`app/libs/sherpa-onnx-2.0.1.aar`，使用 `OnlineRecognizer` 流式接口。
- 默认模型：`Zipformer Small 中英`（int8 encoder/joiner + float decoder）。
- 可切换模型：`Paraformer 中英`（int8 encoder/decoder）。切换时释放旧模型，
  一次只加载一款；已确认字幕保留，临时识别段重新开始。
- 模型位于 `app/src/main/assets/models/`，直接打入 APK，运行时无需联网。
  包含两款模型及四个 ABI 的 native 库后，当前 debug APK 约 **408 MB**；
  来源与许可说明见 `app/src/main/assets/models/SOURCES.md`。
- 字幕区中，未遇到 endpoint 的文本是当前临时结果，endpoint 后逐行确认。
  主界面可关闭识别或清空字幕，不影响音频播放。

## 本地音频转写调试

点主界面 **本地音频转写**，选择文件和模型后即可脱离 BLE 调试识别：

- 支持 WAV、ADTS AAC/M4A、Ogg Opus 等系统可解码的音频容器。
- 裸 PCM 使用 `.pcm` 扩展名，并按界面选择采样率和声道数。
- 文件通过 SAF 读取，使用 `MediaExtractor + MediaCodec` 解码，之后与 BLE
  实时流共用 `PcmNormalizer` 和 `SpeechRecognizer`。
- 可显示进度、取消任务、切换模型；**导出文字**写入应用私有目录
  `Android/data/com.ambiq.blevideoplayer/files/asr_*.txt`，无需存储权限。

本地页只使用设备收到的音频，不申请 `RECORD_AUDIO` 权限。

## 代码结构

```
app/src/main/java/com/ambiq/blevideoplayer/
├── MainActivity.kt         BLE 主界面、音视频控制、实时字幕与生命周期
├── BleManager.kt           BLE 协商、双 TX 串行订阅、通知分发与自动重连
├── VideoStreamDecoder.kt   Annex B NAL 重组 + 视频硬解 + 有界队列
├── AudioStreamDecoder.kt   PCM/AAC/Opus 解码、AudioTrack 播放与音频队列
├── PcmNormalizer.kt        单声道下混 + 连续重采样到 16 kHz
├── SpeechRecognizer.kt     sherpa-onnx 会话、有界输入、partial/final 与模型切换
├── AsrModel.kt             两款 assets 模型配置
├── LocalAsrActivity.kt     本地转写界面、取消/进度/导出
└── LocalAudioDecoder.kt    本地容器与裸 PCM 解码
```

线程模型：BLE 回调线程 → 音视频各自的有界队列（不阻塞 BLE，满时丢最旧数据）
→ 各自解码线程 → 音频归一化 PCM → 独立识别线程 → 主线程刷新 UI 与每秒统计。
