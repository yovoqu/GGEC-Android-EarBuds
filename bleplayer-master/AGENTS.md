# AGENTS.md — BleVideoPlayer

面向 AI 编码代理的项目说明。阅读本文前无需了解本项目。

## 项目概述

BleVideoPlayer 是配合 Ambiq BLE 固件的 **Android 实时音视频播放与端侧语音转写 App**（Kotlin 单模块工程）。

- 视频：BLE Notify 推送连续 **H.264/H.265 Annex B 裸码流**（无私有帧头、无长度字段、无时间戳）。App 按起始码恢复完整 NAL，`MediaCodec` 硬解并渲染到 `TextureView`。
- 音频：独立 BLE Notify 推送 **PCM / ADTS AAC / Opus**。App 解码后可用 `AudioTrack` 播放，并统一转成 16 kHz 单声道 float PCM。
- ASR：随 APK 打包 sherpa-onnx 2.0.1 和两款中英双语流式模型，实时显示 partial/final 文本，支持模型切换与本地音频独立转写。
- 包名 / applicationId：`com.ambiq.blevideoplayer`；模块 `:app`；根工程 `BleVideoPlayer`。

### 关键 UUID

| 项目 | 值 |
|---|---|
| 广播名 | `dabaicai--test` |
| Service UUID | `00002760-08c2-11e1-9073-0e8ac72e5450` |
| 视频 TX（Notify） | `00002760-08c2-11e1-9073-0e8ac72e5402`（固件约定，勿改） |
| 音频 TX（Notify） | 默认 `00002760-08c2-11e1-9073-0e8ac72e5403`，界面可配置并持久化 |
| RX（Write） | `00002760-08c2-11e1-9073-0e8ac72e5401` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |

## 技术栈

- Kotlin 2.0.20（JVM target 17），Gradle Kotlin DSL。
- Android Gradle Plugin 8.5.2，Gradle 8.7+；**仓库未提交 Gradle Wrapper**。
- compileSdk 34 / targetSdk 34 / minSdk 26。
- AndroidX：`core-ktx:1.13.1`、`appcompat:1.7.0`；UI 为系统控件，无 Compose。
- 本地 AAR：`app/libs/sherpa-onnx-2.0.1.aar`。
- 核心 API：`BluetoothGatt`、`MediaCodec`、`MediaExtractor`、`AudioTrack`、`TextureView`、SAF。

## 构建与运行

推荐用 Android Studio Ladybug (2024.2)+ 打开本目录，Gradle JDK 必须为 **17**。

命令行构建（需本机已有 Gradle 8.7+ 与 Android SDK）：

```sh
gradle assembleDebug
gradle lintDebug
```

无测试代码、无 CI 配置；不要新增测试脚手架，除非用户明确要求。验证方式为构建、lint 和真机运行。完整音视频 BLE 验证需要支持音频 TX 的新固件。

模型与 AAR 直接打入 APK，包含四个 ABI 后当前 debug APK 约 408 MB。`.onnx` 已在 `aaptOptions.noCompress` 中排除压缩，供 native AssetManager 读取。

## 代码结构

```
app/src/main/java/com/ambiq/blevideoplayer/
├── MainActivity.kt         BLE 主界面、音视频参数、字幕、录制与生命周期
├── BleManager.kt           扫描/连接/协商、双 TX 串行订阅、通知分发、自动重连
├── VideoStreamDecoder.kt   跨 Notify 重组 NAL + 视频硬解 + 4 MB 队列
├── AudioStreamDecoder.kt   PCM/AAC/Opus 解码、AudioTrack 播放 + 1 MB 队列
├── PcmNormalizer.kt        PCM 下混 + 跨块连续重采样到 16 kHz
├── SpeechRecognizer.kt     sherpa-onnx 流式会话、有界输入、partial/final
├── AsrModel.kt             两款模型的 assets 路径与 OnlineRecognizerConfig
├── LocalAsrActivity.kt     独立本地转写页、进度/取消/导出
└── LocalAudioDecoder.kt    MediaExtractor 容器解码与 .pcm 原始音频读取
```

线程模型（改动时务必保持）：

```
BLE binder 回调 → 音视频各自有界队列（绝不阻塞 BLE）
                → 各自解码线程 → 16 kHz 单声道 PCM
                → speech-recognizer 独立推理线程
                → uiHandler / runOnUiThread 刷新 UI
```

## 关键实现约束

### 视频

- `MediaCodec` 输入按完整 NAL 对齐；同时识别 3/4 字节 Annex B 起始码，不提交 0 字节空 buffer。
- 裸流自带参数集，configure 不设置视频 csd；真实分辨率从 `INFO_OUTPUT_FORMAT_CHANGED` 和 crop 区读取。
- 直播时间戳使用提交时 `System.nanoTime()/1000`。
- H.264/H.265 按钮只重建本地解码器，不向固件协商。
- 视频队列 4 MB，溢出丢最旧 NAL 并计数；视频格式诊断只看 `videoRxBytes`，不能误用音频字节。

### 音频

- 视频 TX 与音频 TX 独立；服务发现后串行写 CCCD，不能并发写 descriptor。缺少其中一个 TX 不影响另一个。
- PCM：16-bit 小端，采样率和单/双声道由界面配置；Notify 可任意切片，需保留奇数残字节。
- AAC：必须是 ADTS，支持跨 Notify 和粘包，恢复完整帧后交给 MediaCodec。
- Opus：**一次 Notify 恰好一个完整编码包**，不支持跨包/粘包；按界面声道配置，输出按 48 kHz 处理。
- 音频队列 1 MB，溢出丢最旧数据；编码/参数改变时重建解码器并清空旧队列。
- `PcmNormalizer` 保留下混与重采样的跨块状态；实时流和本地文件必须共用此类。
- 播放可静音，静音不能停止 ASR；音频接收不申请麦克风权限。

### ASR 与模型

- 默认 `AsrModel.ZIPFORMER_SMALL`，可切换 `AsrModel.PARAFORMER`；模型配置集中在 `AsrModel.kt`，不要把路径散落在 UI。
- 模型在 `app/src/main/assets/models/`，所需文件必须与配置一致。来源与许可说明见该目录 `SOURCES.md`。
- 一次只加载一个 `OnlineRecognizer`；切换模型先停止并释放旧实例，模型加载和推理不能在 UI 线程。
- 使用 `isReady/decode/getResult/isEndpoint/reset` 流程；区分临时文本和 endpoint 后确认文本。
- Paraformer endpoint/结束时需补右上下文静音；本地文件读完调用 `inputFinished()` 并取最终结果。
- ASR 输入队列有界，推理落后不能反压 BLE。
- 本地页使用 SAF，不申请存储权限；`.pcm` 才按裸 PCM 读取，其余格式走 `MediaExtractor`。

### BLE 与生命周期

- 保留 API 33 前后两套 `onCharacteristicChanged` / `writeDescriptor` 签名。
- 运行时权限集中在 `MainActivity`，不要移除 `BleManager` 的 `@SuppressLint("MissingPermission")`。
- BLE 回调在 binder 线程，UI 操作必须切主线程。
- 暂停/销毁时停止解码器和识别器，释放 MediaCodec、AudioTrack、OnlineStream、OnlineRecognizer。
- RX 下行仍是 ASCII 文本，固件配置协议未定义；不要自行假定新的二进制命令。

## 代码风格

- 注释、文档、UI 文案使用中文；文件头用块注释说明职责与关键约定。
- Kotlin official code style，4 空格缩进；硬编码常量集中在 `companion object`。
- 不引入未确认的新依赖；sherpa-onnx 使用官方本地 AAR。

## 权限与文件

仅需原 BLE 权限：Android 12+ `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`；Android 11 及以下同时申请 `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` 并使用旧蓝牙权限。

不需要 `RECORD_AUDIO`、联网或存储权限。视频录制和 ASR 文本导出写 `getExternalFilesDir`，可用 adb pull 取出。

## 测试与调试

- 无自动化测试；构建后优先用本地音频页分别验证 WAV、AAC、Opus 与两款模型。
- logcat TAG：`VideoStreamDecoder`、`AudioStreamDecoder`、`SpeechRecognizer`。
- 真机回归：原 H.264/H.265、纯音频、音视频并行、模型切换、静音、断线重连和 RX 文本发送。
- 当前若没有新版固件或 Android 真机，不能把本地代码检查描述成 BLE 端到端验证通过。
