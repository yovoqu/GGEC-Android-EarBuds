# biz_ggec_record

Ambiq BLE 音视频接收 + 端侧 ASR 业务库（无 UI）。

## 约束

- 语言：Java 21
- BLE：仅通过 `provider/lib_bluetooth` 使用 `lib_bluetooth`
- ASR：模型在 `src/main/assets/models/`；sherpa-onnx 经独立模块 `:sherpa_onnx` 引入（AGP 禁止 library 直接 `files('*.aar')`）
- 公开面：`YoRecordSdk` / `api/` / `model/`；禁止外部引用 `internal/`、`provider/`

## 与 HS 的关系

与 `biz_ggec_hs` 并列，GATT UUID 不同，会话互不共用。  
HS 的 `filerecordcmd` / `streamrecordcmd` 仍属耳机协议，不在本库。

## Phase 状态

- Phase 1：模块骨架、边界校验、扫描、独立 app 入口
- Phase 2：`YoBleRecordLink` 连接协商（HIGH / MTU 517 / 2M PHY）+ 串行双 TX 订阅 + RX 文本写 + 吞吐统计
- Phase 3：`VideoStreamDecoder` / `AudioStreamDecoder` / `PcmNormalizer` 硬解播放；独立页 TextureView
- Phase 4（当前）：sherpa-onnx 流式 ASR（Zipformer / Paraformer），字幕经 `RecordSessionObserver`
