# 语音识别引擎与模型来源

## sherpa-onnx 2.0.1

- 项目：https://github.com/k2-fsa/sherpa-onnx
- Android AAR：https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar
- 引擎代码使用 Apache License 2.0，许可见上游仓库 `LICENSE` 文件。

## Zipformer Small 中英双语流式模型

- 目录：`sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16`
- 官方模型页：https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16-bilingual-chinese-english
- 下载包：https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2
- 上游模型：https://huggingface.co/csukuangfj/k2fsa-zipformer-bilingual-zh-en-t
- APK 仅包含 int8 encoder、float decoder、int8 joiner 和 tokens.txt。

## Paraformer 中英双语流式模型

- 目录：`sherpa-onnx-streaming-paraformer-bilingual-zh-en`
- 官方模型页：https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-paraformer/paraformer-models.html#csukuangfjsherpa-onnx-streaming-paraformer-bilingual-zh-en-chinese-english
- 模型仓库：https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en
- 原始模型：https://www.modelscope.cn/models/damo/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8404-online/summary
- APK 仅包含 int8 encoder、int8 decoder 和 tokens.txt。

模型权重不等同于 sherpa-onnx 引擎代码，其再分发与使用应遵循各上游模型的条款。公开发布包含这些权重的 APK 前，需再次核对上游模型页中的许可信息。
