package com.yovo.biz_ggec_hs.internal.record.assembler;

/**
 * HS02 音频 Notify 格式自动识别器。
 * <p>
 * 基于首包特征或包长与包序号指纹精准识别空口格式，并保持整会话锁定。
 */
final class HsAudioFormatDetector {

    private HsAudioFormatDetector() {
    }

    static HsAudioNotifyFormat detect(byte[] chunk, int pendingBytes,
                                      HsAudioPacketTracker indexTracker) {
        if (chunk == null || chunk.length < HsAudioFrameContract.SUBFRAME_44) {
            return null;
        }
        return detectFromNotifyLength(chunk, indexTracker);
    }

    private static HsAudioNotifyFormat detectFromNotifyLength(byte[] chunk,
                                                              HsAudioPacketTracker tracker) {
        int len = chunk.length;
        if (len == HsAudioNotifyFormat.STANDARD_LEGACY.getNotifyBytes()) {
            return HsAudioNotifyFormat.STANDARD_LEGACY;
        }
        if (len == HsAudioNotifyFormat.HS02_FULL_FRAME.getNotifyBytes()
                || len == HsAudioNotifyFormat.HS02_FULL_FRAME.getSubframeBytes()) {
            return HsAudioNotifyFormat.HS02_FULL_FRAME;
        }
        if (len == HsAudioNotifyFormat.HS02_QUAD_40_176.getNotifyBytes()) {
            return detect176Layout(chunk, tracker);
        }
        if (len == HsAudioNotifyFormat.HS02_DUAL_HALF_168.getNotifyBytes()) {
            return detect168Layout(chunk, tracker);
        }
        return null;
    }

    private static HsAudioNotifyFormat detect176Layout(byte[] chunk,
                                                       HsAudioPacketTracker tracker) {
        HsAudioNotifyFormat format = HsAudioNotifyFormat.HS02_QUAD_40_176;
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

    private static HsAudioNotifyFormat detect168Layout(byte[] chunk,
                                                       HsAudioPacketTracker tracker) {
        int idx0 = readLeUint32(chunk, 0);
        int idx84 = readLeUint32(chunk,
                HsAudioNotifyFormat.HS02_DUAL_HALF_168.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx84 == idx0 + 1) {
            return HsAudioNotifyFormat.HS02_DUAL_HALF_168;
        }
        int idx164 = readLeUint32(chunk,
                HsAudioNotifyFormat.HS02_FULL_FRAME.getSubframeBytes());
        if (tracker.isPlausible(idx0) && idx164 == idx0 + 1) {
            return HsAudioNotifyFormat.HS02_MTU_SPLIT;
        }
        if (tracker.isPlausible(idx0)) {
            return HsAudioNotifyFormat.HS02_FULL_FRAME;
        }
        return HsAudioNotifyFormat.STANDARD_LEGACY;
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
