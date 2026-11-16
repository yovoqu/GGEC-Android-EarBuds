package com.yovo.biz_ggec_record.internal.asr;

import androidx.annotation.NonNull;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import com.yovo.biz_ggec_record.model.RecordAsrModel;

/**
 * 把 {@link RecordAsrModel} 映射为 sherpa-onnx {@link OnlineRecognizerConfig}。
 */
public final class AsrModelConfigs {
    private AsrModelConfigs() {
    }

    @NonNull
    public static OnlineRecognizerConfig recognizerConfig(@NonNull RecordAsrModel model) {
        String dir = model.assetsDir();
        OnlineModelConfig modelConfig = new OnlineModelConfig();
        switch (model) {
            case ZIPFORMER_SMALL -> {
                OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
                transducer.setEncoder(dir + "/encoder-epoch-99-avg-1.int8.onnx");
                transducer.setDecoder(dir + "/decoder-epoch-99-avg-1.onnx");
                transducer.setJoiner(dir + "/joiner-epoch-99-avg-1.int8.onnx");
                modelConfig.setTransducer(transducer);
                modelConfig.setTokens(dir + "/tokens.txt");
                modelConfig.setModelType("zipformer");
                modelConfig.setNumThreads(2);
            }
            case PARAFORMER -> {
                OnlineParaformerModelConfig paraformer = new OnlineParaformerModelConfig();
                paraformer.setEncoder(dir + "/encoder.int8.onnx");
                paraformer.setDecoder(dir + "/decoder.int8.onnx");
                modelConfig.setParaformer(paraformer);
                modelConfig.setTokens(dir + "/tokens.txt");
                modelConfig.setModelType("paraformer");
                modelConfig.setNumThreads(2);
            }
        }

        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(16_000);
        feat.setFeatureDim(80);

        OnlineRecognizerConfig config = new OnlineRecognizerConfig();
        config.setFeatConfig(feat);
        config.setModelConfig(modelConfig);
        config.setEnableEndpoint(true);
        return config;
    }
}
