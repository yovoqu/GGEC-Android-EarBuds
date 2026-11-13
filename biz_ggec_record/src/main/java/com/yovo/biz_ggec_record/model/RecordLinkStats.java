package com.yovo.biz_ggec_record.model;

/**
 * 链路统计快照（约每秒刷新一次）。
 */
public final class RecordLinkStats {

    private final double videoKbps;
    private final double audioKbps;
    private final double videoFps;
    private final int videoQueueKb;
    private final int audioQueueKb;
    private final long videoDropped;
    private final long audioDropped;

    public RecordLinkStats(double videoKbps,
                           double audioKbps,
                           double videoFps,
                           int videoQueueKb,
                           int audioQueueKb,
                           long videoDropped,
                           long audioDropped) {
        this.videoKbps = videoKbps;
        this.audioKbps = audioKbps;
        this.videoFps = videoFps;
        this.videoQueueKb = videoQueueKb;
        this.audioQueueKb = audioQueueKb;
        this.videoDropped = videoDropped;
        this.audioDropped = audioDropped;
    }

    public static RecordLinkStats empty() {
        return new RecordLinkStats(0, 0, 0, 0, 0, 0, 0);
    }

    public double videoKbps() {
        return videoKbps;
    }

    public double audioKbps() {
        return audioKbps;
    }

    public double videoFps() {
        return videoFps;
    }

    public int videoQueueKb() {
        return videoQueueKb;
    }

    public int audioQueueKb() {
        return audioQueueKb;
    }

    public long videoDropped() {
        return videoDropped;
    }

    public long audioDropped() {
        return audioDropped;
    }
}
