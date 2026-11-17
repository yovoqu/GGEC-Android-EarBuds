package com.yovo.biz_ggec_wy.internal.record.assembler;

import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

/**
 * 音频数据包序号连续性追踪器。
 */
final class WyAudioPacketTracker {

    private static final String TAG = "WyAudioPacketTracker";

    private int lastDataIndex = -1;
    private boolean allowForwardResync;

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
            WyLog.w(TAG, "dataIndex 前向跳号: " + lastDataIndex + " -> " + dataIndex
                    + "（缺口=" + (dataIndex - lastDataIndex - 1)
                    + "，allowForwardResync=" + allowForwardResync + "）");
        }
        lastDataIndex = dataIndex;
    }

    boolean isPlausible(int dataIndex) {
        if (dataIndex < WyAudioFrameContract.STARTING_DATA_INDEX) {
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
        if (dataIndex < WyAudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastDataIndex < 0) {
            return dataIndex == 0 || dataIndex == 1;
        }
        return dataIndex == lastDataIndex + 1;
    }
}
