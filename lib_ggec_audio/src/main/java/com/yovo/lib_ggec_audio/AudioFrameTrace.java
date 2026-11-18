package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;

/**
 * 帧装配的观测点：lib 层不认识日志，产品侧把这三种事实接进自己的日志缝。
 *
 * <p>三者都是「已经发生的事实」，不是异常：格式锁定、无法识别线格式、包序号前向跳号。
 * 实现方只允许记录，不得回写装配器状态。</p>
 */
public interface AudioFrameTrace {

    /** 不观测。 */
    AudioFrameTrace NONE = new AudioFrameTrace() {
        @Override
        public void onFormatLocked(@NonNull AudioNotifyFormat format) {
        }

        @Override
        public void onFormatUnresolved(int chunkBytes, int pendingBytes) {
        }

        @Override
        public void onDataIndexGap(int fromIndex, int toIndex, int framesPerNotify) {
        }

        @Override
        public void onChunkProcessed(@NonNull byte[] chunk, int framesProduced, int firstIndex) {
        }
    };

    /** 首包判定成功，本会话锁定该线格式。 */
    void onFormatLocked(@NonNull AudioNotifyFormat format);

    /** 首包判定失败：该切片无法归入任何已知线格式。 */
    void onFormatUnresolved(int chunkBytes, int pendingBytes);

    /**
     * 包序号出现真实前向跳号（按每包帧数折算后仍缺帧）。
     *
     * @param fromIndex       上一包首帧序号
     * @param toIndex         本包首帧序号
     * @param framesPerNotify 每包帧数（缺口 = toIndex - fromIndex - framesPerNotify）
     */
    void onDataIndexGap(int fromIndex, int toIndex, int framesPerNotify);

    /**
     * 每个切片处理完毕后的结构化事实（诊断用）。
     *
     * <p>lib 只上报事实，不做打印决策：是否输出、以什么级别输出、是否附带原始字节、
     * 采样频率，全部由 {@code LoggingAudioFrameTrace} 的实现决定。
     * 这样「数据可打印」与「日志不刷屏」两个诉求同时满足，且散点诊断不会回流到 biz。</p>
     *
     * @param chunk         原始切片字节（只读；回调返回后引用失效，实现方不得留存）
     * @param framesProduced 本次还原出的完整 Opus 帧数
     * @param firstIndex    本切片首帧序号；无法判定时为 -1
     */
    default void onChunkProcessed(@NonNull byte[] chunk, int framesProduced, int firstIndex) {
        // 默认不观测：切片级诊断是可选能力，需要的实现（如 LoggingAudioFrameTrace）自行覆盖。
    }
}
