package com.yovo.lib_ggec_audio;

import androidx.annotation.Nullable;

/**
 * 音频 Notify 线格式自动识别器：按包长与包序号指纹判定，判定结果整会话锁定。
 *
 * <p>判定顺序固定：先按包长映射（长强于序号，序号只用于区分同一包长下的布局），
 * 包长不认识但缓冲区里已有残段时才回落到 MTU 拆分——那是「上一包把帧切断了」的唯一解释。</p>
 */
final class AudioFormatDetector {

    private AudioFormatDetector() {
    }

    @Nullable
    static AudioNotifyFormat detect(@Nullable byte[] chunk, int pendingBytes,
                                    AudioPacketTracker indexTracker) {
        if (chunk == null || chunk.length < AudioFrameContract.SUBFRAME_44) {
            return null;
        }
        AudioNotifyFormat byLength = detectFromNotifyLength(chunk, indexTracker);
        if (byLength != null) {
            return byLength;
        }
        if (pendingBytes > 0) {
            return AudioNotifyFormat.MTU_SPLIT;
        }
        return null;
    }

    @Nullable
    private static AudioNotifyFormat detectFromNotifyLength(byte[] chunk,
                                                            AudioPacketTracker tracker) {
        int len = chunk.length;
        if (len == AudioNotifyFormat.STANDARD_LEGACY.getNotifyBytes()) {
            return AudioNotifyFormat.STANDARD_LEGACY;
        }
        if (len == AudioNotifyFormat.FULL_FRAME.getNotifyBytes()
                || len == AudioNotifyFormat.FULL_FRAME.getSubframeBytes()) {
            return AudioNotifyFormat.FULL_FRAME;
        }
        if (len == AudioNotifyFormat.QUAD_40_176.getNotifyBytes()) {
            return detect176Layout(chunk, tracker);
        }
        if (len == AudioNotifyFormat.DUAL_HALF_168.getNotifyBytes()) {
            return detect168Layout(chunk, tracker);
        }
        return null;
    }

    @Nullable
    private static AudioNotifyFormat detect176Layout(byte[] chunk,
                                                     AudioPacketTracker tracker) {
        AudioNotifyFormat format = AudioNotifyFormat.QUAD_40_176;
        int step = format.getSubframeBytes();
        int idx0 = readLeUint32(chunk, 0);
        if (!tracker.isPlausible(idx0)) {
            return null;
        }
        for (int i = 1; i < format.getFramesPerNotify(); i++) {
            int idx = readLeUint32(chunk, i * step);
            if (idx != idx0 + i) {
                return null;
            }
        }
        return format;
    }

    private static AudioNotifyFormat detect168Layout(byte[] chunk,
                                                     AudioPacketTracker tracker) {
        int idx0 = readLeUint32(chunk, 0);
        int idx84 = readLeUint32(chunk, AudioNotifyFormat.DUAL_HALF_168.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx84 == idx0 + 1) {
            return AudioNotifyFormat.DUAL_HALF_168;
        }
        int idx164 = readLeUint32(chunk, AudioNotifyFormat.FULL_FRAME.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx164 == idx0 + 1) {
            return AudioNotifyFormat.MTU_SPLIT;
        }
        if (tracker.isPlausible(idx0)) {
            return AudioNotifyFormat.FULL_FRAME;
        }
        return AudioNotifyFormat.STANDARD_LEGACY;
    }

    static int readLeUint32(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 3 >= data.length) {
            return -1;
        }
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
