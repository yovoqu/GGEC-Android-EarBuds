package com.yovo.biz_ggec_hs.internal.center.broadcast;

/**
 * 广播层对模块内部的唯一接缝：单帧识别与流式会话。代际解码器仍只在本包内组合。
 */
public final class HsBroadcastRuntime {
    private static final HsBroadcastClassifier CLASSIFIER = HsBroadcastRegistry.standard();

    private HsBroadcastRuntime() {
    }

    public static HsBroadcastReport classify(HsScanSnapshot snapshot) {
        return CLASSIFIER.classify(snapshot);
    }

    public static HsBroadcastSession openSession() {
        return new HsBroadcastSessionImpl(
                CLASSIFIER, new Hs02BroadcastAssembler(), System::nanoTime);
    }
}
