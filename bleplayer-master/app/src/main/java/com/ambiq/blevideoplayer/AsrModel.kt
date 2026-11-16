package com.ambiq.blevideoplayer

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/** 随 APK 打包的中英双语流式模型，运行时一次只加载其中一款。 */
enum class AsrModel(val displayName: String, val assetDir: String) {
    ZIPFORMER_SMALL(
        "Zipformer Small 中英",
        "models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16"
    ),
    PARAFORMER(
        "Paraformer 中英",
        "models/sherpa-onnx-streaming-paraformer-bilingual-zh-en"
    );

    fun recognizerConfig(): OnlineRecognizerConfig {
        val modelConfig = when (this) {
            ZIPFORMER_SMALL -> OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$assetDir/encoder-epoch-99-avg-1.int8.onnx",
                    decoder = "$assetDir/decoder-epoch-99-avg-1.onnx",
                    joiner = "$assetDir/joiner-epoch-99-avg-1.int8.onnx"
                ),
                tokens = "$assetDir/tokens.txt",
                modelType = "zipformer",
                numThreads = 2
            )
            PARAFORMER -> OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = "$assetDir/encoder.int8.onnx",
                    decoder = "$assetDir/decoder.int8.onnx"
                ),
                tokens = "$assetDir/tokens.txt",
                modelType = "paraformer",
                numThreads = 2
            )
        }
        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = modelConfig,
            enableEndpoint = true
        )
    }
}
