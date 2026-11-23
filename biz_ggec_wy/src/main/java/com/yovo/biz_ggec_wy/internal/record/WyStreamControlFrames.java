package com.yovo.biz_ggec_wy.internal.record;

/**
 * WY 随录随传控制帧的唯一信源。
 *
 * <p>控制帧是 <b>8 字节定长</b>：</p>
 * <pre>
 *   99 EC D5/D6 | 00 01 | 00(状态) | 12 34
 *   命令码        | 长度域 | 状态      | 固定尾
 * </pre>
 *
 * <p>D5 = 开始、D6 = 停止；状态字节落在帧内下标 {@link #STATUS_INDEX}（= 5）。
 * 与 HS 的 Start/StopStreamRecordCommand 完全对齐。此前这些字节散落在
 * {@code WyStreamRecordSession} 内联数组和 44/8 魔法数里，本类收口成单一来源。</p>
 */
final class WyStreamControlFrames {

    /** 控制帧固定长度（字节）。 */
    static final int FRAME_LEN = 8;

    /** 状态字节在帧内的下标。 */
    static final int STATUS_INDEX = 5;

    /** 开始命令码（99 EC <b>D5</b> …）。 */
    static final int CMD_START = 0xD5;

    /** 停止命令码（99 EC <b>D6</b> …）。 */
    static final int CMD_STOP = 0xD6;

    /** 开始随录随传：99 EC D5 00 01 00 12 34。 */
    static final byte[] START = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0xD5, 0x00, 0x01, 0x00, 0x12, 0x34
    };

    /** 停止随录随传：99 EC D6 00 01 00 12 34。 */
    static final byte[] STOP = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0xD6, 0x00, 0x01, 0x00, 0x12, 0x34
    };

    private WyStreamControlFrames() {
    }

    /** 是否恰为一个完整的「停止」控制帧（8 字节全等）。 */
    static boolean isStopFrame(byte[] chunk) {
        return matches(chunk, STOP);
    }

    /** 是否恰为一个完整的控制帧（开始或停止，8 字节全等）。 */
    static boolean isAnyControlFrame(byte[] chunk) {
        return matches(chunk, START) || matches(chunk, STOP);
    }

    /**
     * 读取控制帧的状态字节。
     *
     * @param frame      承载控制帧的字节数组
     * @param baseOffset 帧首在本数组中的起始下标
     * @return 状态字节（0..255）；越界返回 {@code -1}
     */
    static int statusAt(byte[] frame, int baseOffset) {
        if (frame == null || baseOffset < 0 || baseOffset + STATUS_INDEX >= frame.length) {
            return -1;
        }
        return frame[baseOffset + STATUS_INDEX] & 0xFF;
    }

    private static boolean matches(byte[] chunk, byte[] expected) {
        if (chunk == null || chunk.length != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (chunk[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
