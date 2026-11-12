package com.yovo.biz_ggec_hs.internal.center.broadcast;

/**
 * 扫描流入口：先按地址拼 HS02 分片，再交给同一套代际解码器。
 */
final class HsBroadcastSessionImpl implements HsBroadcastSession {
    private final HsBroadcastClassifier classifier;
    private final Hs02BroadcastAssembler assembler;
    private final HsBroadcastClock clock;

    HsBroadcastSessionImpl(HsBroadcastClassifier classifier,
                           Hs02BroadcastAssembler assembler,
                           HsBroadcastClock clock) {
        this.classifier = classifier;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Override
    public synchronized HsBroadcastReport observe(HsScanSnapshot snapshot) {
        return classifier.classify(assembler.fold(snapshot, clock.nanoTime()));
    }

    @Override
    public synchronized void clear() {
        assembler.clear();
    }
}
