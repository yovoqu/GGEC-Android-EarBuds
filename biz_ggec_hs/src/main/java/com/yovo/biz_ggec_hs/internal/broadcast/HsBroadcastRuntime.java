package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

/**
 * 广播层对根入口暴露的唯一接缝。代际解码器仍只在本包内组合。
 */
public final class HsBroadcastRuntime {
    private static final HsBroadcastClassifier CLASSIFIER = HsBroadcastRegistry.standard();

    private HsBroadcastRuntime() {
    }

    @NonNull
    public static HsBroadcastReport classify(@NonNull HsScanSnapshot snapshot) {
        return CLASSIFIER.classify(snapshot);
    }

    @NonNull
    public static YoHsBroadcastSession openSession() {
        return new HsBroadcastSessionImpl(
                CLASSIFIER, new Hs02BroadcastAssembler(), System::nanoTime);
    }
}
