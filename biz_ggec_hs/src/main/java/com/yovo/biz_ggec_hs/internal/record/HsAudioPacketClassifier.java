package com.yovo.biz_ggec_hs.internal.record;

import androidx.annotation.Nullable;

/**
 * HS02 音频数据通道（820003）报文分类器。
 * <p>
 * 职责：严格区分 820003 特征值上的“控制上报包（Control Report）”与“音频编码切片（Audio Chunk）”。
 * 最小音频切片子帧为 44 字节（4B index + 40B Opus）。长度小于等于 20 字节或携带 0x80 高字节的包
 * 均为固件内部控制/状态上报，绝不能流入音频拆包器，否则会导致线格式误判并污染音频解码缓存。
 */
public final class HsAudioPacketClassifier {

    /** 控制响应/上报包的最大长度（bytes） */
    public static final int CONTROL_PACKET_MAX_BYTES = 20;

    /** 固件主动控制上报的高字节标识 */
    public static final byte REMOTE_REPORT_CMD_HI = (byte) 0x80;

    /** 控制上报命令类型（小端 uint16） */
    public static final int CMD_TYPE_START = 0x8004;
    public static final int CMD_TYPE_STOP = 0x8005;
    public static final int CMD_TYPE_DELAY = 0x8012;

    private HsAudioPacketClassifier() {
    }

    /**
     * 判断是否为非音频控制报文。
     * 控制报文格式：cmdLo + 0x80 + dataLen(2) + extData，长度必须在 [4, 20] 字节之间。
     * 音频切片最小为 44 字节（4B index + 40B Opus），因此长度 > 20 的绝不是控制包。
     */
    public static boolean isControlPacket(@Nullable byte[] value) {
        if (value == null || value.length < 4 || value.length > CONTROL_PACKET_MAX_BYTES) {
            return false;
        }
        return value[1] == REMOTE_REPORT_CMD_HI;
    }

    /**
     * 解析控制上报的命令类型。
     *
     * @return {@link #CMD_TYPE_START}、{@link #CMD_TYPE_STOP}、{@link #CMD_TYPE_DELAY}，无法识别返回 0
     */
    public static int resolveControlCmdType(@Nullable byte[] value) {
        if (!isControlPacket(value)) {
            return 0;
        }
        return (value[0] & 0xFF) | ((value[1] & 0xFF) << 8);
    }
}
