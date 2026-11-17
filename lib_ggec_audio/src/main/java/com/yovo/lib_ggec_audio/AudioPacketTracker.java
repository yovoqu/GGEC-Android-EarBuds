package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;

/**
 * 音频 Notify 包序号连续性追踪器。
 *
 * <p>序号是帧头里的 4 字节小端整数：只接受「下一个」或有限前向跳号；回退一律判为不可信，
 * 这样错位的残段会在装配器里被逐字节滑走，而不是灌进解码器。</p>
 */
final class AudioPacketTracker {

    private final AudioFrameTrace trace;
    private int lastDataIndex = -1;
    private boolean allowForwardResync;

    AudioPacketTracker(@NonNull AudioFrameTrace trace) {
        this.trace = trace;
    }

    void reset() {
        lastDataIndex = -1;
        allowForwardResync = false;
    }

    void setAllowForwardResync(boolean allow) {
        allowForwardResync = allow;
    }

    int getLastDataIndex() {
        return lastDataIndex;
    }

    void accept(int dataIndex) {
        if (lastDataIndex >= 0 && dataIndex > lastDataIndex + 1) {
            trace.onDataIndexGap(lastDataIndex, dataIndex);
        }
        lastDataIndex = dataIndex;
    }

    boolean isPlausible(int dataIndex) {
        if (dataIndex < AudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastDataIndex < 0) {
            return true;
        }
        if (dataIndex == lastDataIndex + 1) {
            return true;
        }
        if (dataIndex <= lastDataIndex) {
            return false;
        }
        if (allowForwardResync) {
            return true;
        }
        return dataIndex - lastDataIndex <= 4;
    }

    boolean isExactNext(int dataIndex) {
        if (dataIndex < AudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastDataIndex < 0) {
            return dataIndex == 0 || dataIndex == 1;
        }
        return dataIndex == lastDataIndex + 1;
    }
}
