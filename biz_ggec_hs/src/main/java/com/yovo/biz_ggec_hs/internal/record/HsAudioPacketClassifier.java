package com.yovo.biz_ggec_hs.internal.record;

import androidx.annotation.Nullable;

/**
 * HS02 音频数据通道（820003）报文分类器。
 * <p>
 * 职责：严格区分 820003 特征值上的“控制上报包（Control Report）”与“音频编码切片（Audio Chunk）”。
 * 最小音频切片子帧为 44 字节（4B index + 40B Opus）。长度小于等于 20 字节或携带 0x80 高字节的包
 * 均为固件内部控制/状态上报，绝不能流入音频拆包器，否则会导致线格式误判并污染音频解码缓存。
 *
 * <p>控制上报命令类型语义（HS02 固件行为）：</p>
 * <ul>
 *   <li>{@link #CMD_TYPE_START}（0x8004）：设备已开始出流，是「随录随传开始」的权威成功信号之一；</li>
 *   <li>{@link #CMD_TYPE_STOP}（0x8005）：设备已停止出流，是「随录随传停止」的成功信号之一；</li>
 *   <li>{@link #CMD_TYPE_DELAY}（0x8012）：设备延时中，仅作信息记录，既不算成功也不算失败。</li>
 * </ul>
 */
public final class HsAudioPacketClassifier {

    /** 控制响应/上报包的最大长度（bytes） */
    public static final int CONTROL_PACKET_MAX_BYTES = 20;

    /** 固件主动控制上报的高字节标识 */
    public static final byte REMOTE_REPORT_CMD_HI = (byte) 0x80;

    /** 控制上报命令类型（小端 uint16）：设备侧开始出流（随录随传开始的权威成功信号）。 */
    public static final int CMD_TYPE_START = 0x8004;

    /** 控制上报命令类型（小端 uint16）：设备侧停止出流（随录随传停止的成功信号）。 */
    public static final int CMD_TYPE_STOP = 0x8005;

    /** 控制上报命令类型（小端 uint16）：设备延时中，仅作信息记录，不代表成败。 */
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

    /** 是否为「设备开始出流」上报（0x8004）。纯函数，供启动成功分支使用。 */
    public static boolean isStartSignal(int cmdType) {
        return cmdType == CMD_TYPE_START;
    }

    /** 是否为「设备停止出流」上报（0x8005）。纯函数，供停止成功分支使用。 */
    public static boolean isStopSignal(int cmdType) {
        return cmdType == CMD_TYPE_STOP;
    }

    /** 是否为「设备延时中」上报（0x8012）。纯函数，仅作信息记录，不代表成败。 */
    public static boolean isDelayNotice(int cmdType) {
        return cmdType == CMD_TYPE_DELAY;
    }

    /**
     * 把命令类型翻成可读语义，供日志与分支判断使用。纯函数，不依赖任何状态。
     *
     * @param cmdType {@link #resolveControlCmdType(byte[])} 的返回值
     * @return 语义描述；无法识别时返回 {@code "未知(0x..)"}
     */
    public static String describeControlCmdType(int cmdType) {
        switch (cmdType) {
            case CMD_TYPE_START:
                return "开始出流(0x8004)";
            case CMD_TYPE_STOP:
                return "停止出流(0x8005)";
            case CMD_TYPE_DELAY:
                return "延时中(0x8012)";
            default:
                return "未知(0x" + Integer.toHexString(cmdType) + ")";
        }
    }
}
