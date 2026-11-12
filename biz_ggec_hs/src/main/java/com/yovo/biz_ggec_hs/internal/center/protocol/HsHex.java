package com.yovo.biz_ggec_hs.internal.center.protocol;

/**
 * 协议帧在命令层使用十六进制文本，过链路时才变成字节。
 */
final class HsHex {
    private static final char[] UPPER = "0123456789ABCDEF".toCharArray();

    private HsHex() {
    }

    static byte[] decode(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("协议帧长度必须是偶数个十六进制字符");
        }
        int length = hex.length() / 2;
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("协议帧包含非十六进制字符");
            }
            out[i] = (byte) ((high << 4) + low);
        }
        return out;
    }

    static String encode(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        char[] out = new char[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            int unsigned = value[i] & 0xFF;
            out[i * 2] = UPPER[unsigned >>> 4];
            out[i * 2 + 1] = UPPER[unsigned & 0x0F];
        }
        return new String(out);
    }
}
