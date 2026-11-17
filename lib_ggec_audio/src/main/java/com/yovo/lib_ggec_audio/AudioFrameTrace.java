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
        public void onDataIndexGap(int fromIndex, int toIndex) {
        }
    };

    /** 首包判定成功，本会话锁定该线格式。 */
    void onFormatLocked(@NonNull AudioNotifyFormat format);

    /** 首包判定失败：该切片无法归入任何已知线格式。 */
    void onFormatUnresolved(int chunkBytes, int pendingBytes);

    /** 包序号出现前向跳号（缺口由协议侧补不回来，装配器只记录）。 */
    void onDataIndexGap(int fromIndex, int toIndex);
}
