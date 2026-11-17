package com.bes.opus;

/**
 * Opus音频数据包
 *
 * 封装Opus编码后的音频数据包，包含数据长度、帧大小和实际数据内容。
 * 用于在音频处理过程中传递和管理Opus格式的音频数据。
 *
 * @author all0
 * @since 2017/12/07
 */
public class OpusPacket {

    /**
     * 构造Opus数据包
     * @param len 数据包长度（字节数）
     * @param frameSize 帧大小（样本数）
     * @param packet Opus编码数据字节数组
     */
    public OpusPacket(int len, int frameSize, byte[] packet) {
        this.len = len;
        this.frameSize = frameSize;
        this.packet = packet;
    }

    /** 数据包长度（字节数） */
    public int len;

    /** 帧大小（样本数） */
    public int frameSize;

    /** Opus编码数据字节数组 */
    public byte[] packet;
}
