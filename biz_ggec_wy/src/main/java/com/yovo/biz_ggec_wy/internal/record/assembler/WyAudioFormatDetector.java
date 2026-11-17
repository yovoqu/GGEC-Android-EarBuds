package com.yovo.biz_ggec_wy.internal.record.assembler;

/**
 * WY 音频数据包格式自动识别器。
 */
final class WyAudioFormatDetector {

    private WyAudioFormatDetector() {
    }

    static WyAudioNotifyFormat detect(byte[] chunk, int pendingBytes,
                                      WyAudioPacketTracker indexTracker) {
        if (chunk == null || chunk.length == 0) {
            return null;
        }
        if (pendingBytes > 0) {
            return WyAudioNotifyFormat.WY_MTU_SPLIT;
        }
        return detectFromNotifyLength(chunk, indexTracker);
    }

    private static WyAudioNotifyFormat detectFromNotifyLength(byte[] chunk,
                                                              WyAudioPacketTracker tracker) {
        int len = chunk.length;
        if (len == WyAudioNotifyFormat.STANDARD_LEGACY.getNotifyBytes()) {
            return WyAudioNotifyFormat.STANDARD_LEGACY;
        }
        if (len == WyAudioNotifyFormat.WY_FULL_FRAME.getNotifyBytes()
                || len == WyAudioNotifyFormat.WY_FULL_FRAME.getSubframeBytes()) {
            return WyAudioNotifyFormat.WY_FULL_FRAME;
        }
        if (len == WyAudioNotifyFormat.WY_QUAD_40_176.getNotifyBytes()) {
            return detect176Layout(chunk, tracker);
        }
        if (len == WyAudioNotifyFormat.WY_DUAL_HALF_168.getNotifyBytes()) {
            return detect168Layout(chunk, tracker);
        }
        return null;
    }

    private static WyAudioNotifyFormat detect176Layout(byte[] chunk,
                                                       WyAudioPacketTracker tracker) {
        WyAudioNotifyFormat format = WyAudioNotifyFormat.WY_QUAD_40_176;
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

    private static WyAudioNotifyFormat detect168Layout(byte[] chunk,
                                                       WyAudioPacketTracker tracker) {
        int idx0 = readLeUint32(chunk, 0);
        int idx84 = readLeUint32(chunk,
                WyAudioNotifyFormat.WY_DUAL_HALF_168.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx84 == idx0 + 1) {
            return WyAudioNotifyFormat.WY_DUAL_HALF_168;
        }
        int idx164 = readLeUint32(chunk,
                WyAudioNotifyFormat.WY_FULL_FRAME.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx164 == idx0 + 1) {
            return WyAudioNotifyFormat.WY_MTU_SPLIT;
        }
        if (tracker.isPlausible(idx0)) {
            return WyAudioNotifyFormat.WY_FULL_FRAME;
        }
        return WyAudioNotifyFormat.STANDARD_LEGACY;
    }

    private static int readLeUint32(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 3 >= data.length) {
            return -1;
        }
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
