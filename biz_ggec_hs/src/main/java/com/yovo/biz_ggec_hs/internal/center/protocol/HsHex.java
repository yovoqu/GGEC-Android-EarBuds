package com.yovo.biz_ggec_hs.internal.center.protocol;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 协议帧在命令层使用十六进制文本，过链路时才变成字节。
 */
public final class HsHex {
    private static final char[] UPPER = "0123456789ABCDEF".toCharArray();

    /** 日志预览默认打印的最大字节数（命令/音频通道统一）。 */
    public static final int PREVIEW_BYTES = 48;

    private HsHex() {
    }

    /**
     * 原始字节的可观测预览：空格分隔大写十六进制，超过 {@code maxBytes} 截断并标注总长度。
     *
     * <p>与 WY 的 {@code HexPreview} 对齐：真正的 BLE 数据流在 Biz 接收点打印，lib 层不打印。
     * 只用于日志，不参与协议解析。</p>
     *
     * @param value    原始字节，可为 {@code null}
     * @param maxBytes 最多打印的字节数
     * @return 形如 {@code "99 EC D5 00 01 00 12 34"}；截断时追加 {@code " …(共NB)"}；{@code null} 返回 {@code "null"}
     */
    public static String preview(@Nullable byte[] value, int maxBytes) {
        if (value == null) {
            return "null";
        }
        int n = Math.min(Math.max(maxBytes, 0), value.length);
        StringBuilder sb = new StringBuilder(n * 3 + 12);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", value[i] & 0xFF));
        }
        if (value.length > n) {
            sb.append(" …(共").append(value.length).append("B)");
        }
        return sb.toString();
    }

    public static byte[] decode(String hex) {
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

    public static String encode(byte[] value) {
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
