package com.yovo.biz_ggec_hs.internal.record.assembler;

import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 音频 Notify 包序号连续性追踪器。
 */
final class HsAudioPacketTracker {

    private static final String TAG = "HsAudioPacketTracker";

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
            HsLog.w(TAG, "dataIndex 前向跳号: " + lastDataIndex + " -> " + dataIndex
                    + "（缺口=" + (dataIndex - lastDataIndex - 1)
                    + "，allowForwardResync=" + allowForwardResync + "）");
        }
        lastDataIndex = dataIndex;
    }

    boolean isPlausible(int dataIndex) {
        if (dataIndex < HsAudioFrameContract.STARTING_DATA_INDEX) {
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
        if (dataIndex < HsAudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastDataIndex < 0) {
            return dataIndex == HsAudioFrameContract.STARTING_DATA_INDEX;
        }
        return dataIndex == lastDataIndex + 1;
    }
}
