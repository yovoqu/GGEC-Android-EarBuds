package com.bes.opus;

import android.media.AudioFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Opus音频处理工具类
 *
 * 提供Opus音频数据处理的各种工具方法，包括：
 * - 数据包的封装和解析
 * - 数据类型转换（int/byte/short之间的转换）
 * - 音频参数计算
 * - 字节序处理
 *
 * @author all0
 * @since 2017/12/07
 */
public class OpusUtils {
    /**
     * 将编码数据封装成数据包格式
     * 数据包格式：[长度4字节][帧大小4字节][编码数据]
     * @param encoded 编码后的数据
     * @param len 编码数据长度
     * @param frameSize 帧大小
     * @return 封装后的数据包字节数组
     */
    public static byte[] packetEncodeData(byte[] encoded, int len, int frameSize) {
        byte[] lenField = intTobyte(len);
        byte[] frameSizeField = intTobyte(frameSize);
        byte[] packet = new byte[len + 4 + 4]; // 长度字段4字节 + 帧大小字段4字节 + 数据

        // 写入长度字段（4字节）
        packet[0] = lenField[0];
        packet[1] = lenField[1];
        packet[2] = lenField[2];
        packet[3] = lenField[3];

        // 写入帧大小字段（4字节）
        packet[4] = frameSizeField[0];
        packet[5] = frameSizeField[1];
        packet[6] = frameSizeField[2];
        packet[7] = frameSizeField[3];

        // 写入编码数据
        for (int i = 0; i < len; i++) {
            packet[i + 8] = encoded[i];
        }
        return packet;
    }

    /**
     * 从输入流中读取Opus数据包
     * 按照数据包格式解析：[长度4字节][帧大小4字节][编码数据]
     * @param is 输入流
     * @return 解析后的OpusPacket对象，读取失败返回null
     */
    public static OpusPacket readPacket(InputStream is) {
        byte[] lenField = new byte[4];
        byte[] frameSizeField = new byte[4];
        try {
            // 读取长度字段（4字节）
            int readLen = is.read(lenField);
            if (readLen == -1) {
                return null; // 流结束
            }
            if (readLen != 4) {
                throw new RuntimeException("readPacket error: read packet length error:" + readLen);
            }
            int len = byteToint(lenField);

            // 读取帧大小字段（4字节）
            int readFrameSize = is.read(frameSizeField);
            if (readFrameSize == -1) {
                return null; // 流结束
            }
            if (readFrameSize != 4) {
                throw new RuntimeException("readPacket error: read frame size error:" + readFrameSize);
            }
            int frameSize = byteToint(frameSizeField);

            // 读取编码数据
            byte[] packet = new byte[len];
            int readPacket = is.read(packet);
            if (readPacket != len) {
                throw new RuntimeException("readPacket error: read packet error:" + readPacket);
            }
            return new OpusPacket(len, frameSize, packet);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将整数转换为字节数组（4字节，大端序）
     * @param i 待转换的整数
     * @return 4字节的字节数组
     */
    public static byte[] intTobyte(int i) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
        // 手动实现的版本（已注释）：
        // byte[] bytes = new byte[4];
        // bytes[0] = (byte) (i>>24);
        // bytes[1] = (byte) ((i>>16)&0xFF);
        // bytes[2] = (byte) ((i>>8)&0xFF);
        // bytes[3] = (byte) (i&0xFF);
        // return bytes;
    }

    /**
     * 将字节数组转换为整数（4字节，大端序）
     * @param b 4字节的字节数组
     * @return 转换后的整数
     * @throws RuntimeException 如果字节数组长度不是4
     */
    public static int byteToint(byte[] b) {
        if (b.length != 4) {
            throw new RuntimeException("byteToint error: bytes length must be 4");
        }
        ByteBuffer bb = ByteBuffer.wrap(b);
        return bb.asIntBuffer().get();
        // 手动实现的版本（已注释）：
        // return (((int)b[0])<<24) | (((int)b[1])<<16)
        //         | (((int)b[2])<< 8) |  ((int)b[3]);
    }



    /**
     * 将短整型数组转换为字节数组
     * 每个短整型（2字节）转换为对应的字节表示
     * @param s 短整型数组
     * @return 转换后的字节数组，长度为原数组长度的2倍
     */
    public static byte[] Shorts2Bytes(short[] s) {
        byte bLength = 2; // 每个short占2字节
        byte[] buf = new byte[s.length * bLength];
        for (int iLoop = 0; iLoop < s.length; iLoop++) {
            byte[] temp = getBytes(s[iLoop]);
            for (int jLoop = 0; jLoop < bLength; jLoop++) {
                buf[iLoop * bLength + jLoop] = temp[jLoop];
            }
        }
        return buf;
    }

    /**
     * 将小端 16-bit PCM 字节数组转换为短整型数组
     * @param b 字节数组
     * @return 短整型数组
     */
    public static short[] Bytes2Shorts(byte[] b) {
        if (b == null) {
            return null;
        }
        int len = b.length / 2;
        short[] s = new short[len];
        for (int i = 0; i < len; i++) {
            int low = b[i * 2] & 0xFF;
            int high = b[i * 2 + 1];
            s[i] = (short) ((high << 8) | low);
        }
        return s;
    }

    /**
     * 将短整型转换为字节数组（自动检测字节序）
     * @param s 短整型值
     * @return 2字节的字节数组
     */
    public static byte[] getBytes(short s) {
        return getBytes(s, testCPU());
    }

    /**
     * 将短整型转换为字节数组（指定字节序）
     * @param s 短整型值
     * @param bBigEnding true=大端序，false=小端序
     * @return 2字节的字节数组
     */
    public static byte[] getBytes(short s, boolean bBigEnding) {
        byte[] buf = new byte[2];
        if (bBigEnding) {
            // 大端序：高位字节在前
            for (int i = buf.length - 1; i >= 0; i--) {
                buf[i] = (byte) (s & 0x00ff);
                s >>= 8;
            }
        } else {
            // 小端序：低位字节在前
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0x00ff);
                s >>= 8;
            }
        }
        return buf;
    }

    /**
     * 检测CPU字节序
     * @return true=大端序，false=小端序
     */
    public static boolean testCPU() {
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            // System.out.println("is big endian");
            return true;
        } else {
            // System.out.println("is little endian");
            return false;
        }
    }

    /**
     * 根据PCM数据长度和声道配置计算帧大小
     * @param pcmLength PCM数据长度（字节数）
     * @param channelConfig 声道配置（AudioFormat.CHANNEL_OUT_MONO或CHANNEL_OUT_STEREO）
     * @return 帧大小（每声道的样本数）
     * @throws RuntimeException 如果声道配置不支持
     */
    public static int getFrameSize(int pcmLength, int channelConfig) {
        int channels;
        if (channelConfig == AudioFormat.CHANNEL_OUT_MONO) {
            channels = 1; // 单声道
        } else if (channelConfig == AudioFormat.CHANNEL_OUT_STEREO) {
            channels = 2; // 立体声
        } else {
            throw new RuntimeException("wrong Channel Config");
        }
        return pcmLength / channels;
    }
}
