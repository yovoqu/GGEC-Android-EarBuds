package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;

/**
 * 音频 Notify 包序号连续性追踪器。
 *
 * <p>序号是帧头里的 4 字节小端整数，**按帧递增**：一包 4 帧时相邻两包首帧序号天然相差 4。
 * 因此「差 4」不是丢包——把差 4 报成缺口，正是此前现场日志持续刷「缺口=3」的误报来源。</p>
 *
 * <p>两个游标各司其职，不能混用：</p>
 * <ul>
 *   <li>{@code lastFrameIndex}：最后处理完的帧序号，用于判断「下一个序号是否合理」；</li>
 *   <li>{@code lastNotifyIndex}：上一包的首帧序号，用于计算真实缺口。</li>
 * </ul>
 * <p>真实缺口 = 本包首帧序号 - 上一包首帧序号 - 每包帧数。丢整包时该差值是每包帧数的整数倍。</p>
 */
final class AudioPacketTracker {

    /** 允许的前向跳号上限（以「每包帧数」为单位）：正常 1 倍，容忍最多 4 包连丢。 */
    private static final int MAX_LOSS_MULTIPLE = 4;

    private final AudioFrameTrace trace;
    private int lastFrameIndex = -1;
    private int lastNotifyIndex = -1;
    private int framesPerNotify = -1;
    private boolean allowForwardResync;

    AudioPacketTracker(@NonNull AudioFrameTrace trace) {
        this.trace = trace;
    }

    void reset() {
        lastFrameIndex = -1;
        lastNotifyIndex = -1;
        framesPerNotify = -1;
        allowForwardResync = false;
    }

    void setAllowForwardResync(boolean allow) {
        this.allowForwardResync = allow;
    }

    int getLastDataIndex() {
        return lastFrameIndex;
    }

    /** 已确认的每包帧数；未知时为 -1。 */
    int getFramesPerNotify() {
        return framesPerNotify;
    }

    /** 记录已确认的每包帧数（格式锁定后由装配器告知）。 */
    void setFramesPerNotify(int frames) {
        if (frames > 0) {
            this.framesPerNotify = frames;
        }
    }

    /**
     * 接受一个整包：包首序号为 {@code dataIndex}，承载 {@code frames} 帧。
     *
     * @param dataIndex 本包首帧序号
     * @param frames    本包承载帧数（&gt;0）
     */
    void acceptNotify(int dataIndex, int frames) {
        if (lastNotifyIndex >= 0) {
            int advance = dataIndex - lastNotifyIndex;
            int expected = framesPerNotify > 0 ? framesPerNotify : frames;
            if (advance > expected) {
                trace.onDataIndexGap(lastNotifyIndex, dataIndex, expected);
            }
        }
        lastNotifyIndex = dataIndex;
        lastFrameIndex = dataIndex + Math.max(1, frames) - 1;
    }

    /** 逐帧推进（MTU 拆分 / 旧线格式这种「一帧一帧来」的路径）。 */
    void accept(int dataIndex) {
        if (lastFrameIndex >= 0 && dataIndex > lastFrameIndex + 1) {
            trace.onDataIndexGap(lastFrameIndex, dataIndex, 1);
        }
        lastFrameIndex = dataIndex;
        if (lastNotifyIndex < 0 || dataIndex > lastNotifyIndex) {
            lastNotifyIndex = dataIndex;
        }
    }

    boolean isPlausible(int dataIndex) {
        if (dataIndex < AudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastFrameIndex < 0) {
            return true;
        }
        int advance = dataIndex - lastFrameIndex;
        if (advance <= 0) {
            // 回退一律不可信：错位残段靠逐字节滑移剔除。
            return false;
        }
        if (allowForwardResync) {
            return true;
        }
        // 上限按每包帧数放宽，容忍整包丢失（最多 MAX_LOSS_MULTIPLE 包）。
        int step = framesPerNotify > 0 ? framesPerNotify : 1;
        return advance <= step * MAX_LOSS_MULTIPLE;
    }

    boolean isExactNext(int dataIndex) {
        if (dataIndex < AudioFrameContract.STARTING_DATA_INDEX) {
            return false;
        }
        if (lastFrameIndex < 0) {
            return dataIndex == 0 || dataIndex == 1;
        }
        return dataIndex == lastFrameIndex + 1;
    }
}
