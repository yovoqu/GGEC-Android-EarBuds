package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

/**
 * 扫描流入口：先按地址拼 HS02 分片，再交给同一套代际解码器。
 */
final class HsBroadcastSessionImpl implements YoHsBroadcastSession {
    @NonNull
    private final HsBroadcastClassifier classifier;
    @NonNull
    private final Hs02BroadcastAssembler assembler;
    @NonNull
    private final HsBroadcastClock clock;

    HsBroadcastSessionImpl(@NonNull HsBroadcastClassifier classifier,
                           @NonNull Hs02BroadcastAssembler assembler,
                           @NonNull HsBroadcastClock clock) {
        this.classifier = classifier;
        this.assembler = assembler;
        this.clock = clock;
    }

    @NonNull
    @Override
    public synchronized HsBroadcastReport observe(@NonNull HsScanSnapshot snapshot) {
        return classifier.classify(assembler.fold(snapshot, clock.nanoTime()));
    }

    @Override
    public synchronized void clear() {
        assembler.clear();
    }
}
