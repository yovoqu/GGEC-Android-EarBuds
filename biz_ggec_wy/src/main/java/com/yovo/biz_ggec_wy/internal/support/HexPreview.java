package com.yovo.biz_ggec_wy.internal.support;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Biz 层打印原始数据用的十六进制预览工具。
 *
 * <p>真正的数据流（SPP 控制/音频字节、PCM 解码字节）在 Biz 处理数据时打印，lib 层不打印。
 * 这里统一格式：空格分隔大写十六进制，超过上限截断并标注总字节数，避免整包 dump 淹没日志。</p>
 */
public final class HexPreview {

    private HexPreview() {
    }

    /**
     * 生成十六进制预览。
     *
     * @param data     原始字节
     * @param maxBytes 最多打印的字节数
     * @return 形如 {@code "00 00 00 …(共440B)"}；{@code data} 为 null 时返回 {@code "null"}
     */
    public static String of(@Nullable byte[] data, int maxBytes) {
        if (data == null) {
            return "null";
        }
        int n = Math.min(Math.max(maxBytes, 0), data.length);
        StringBuilder sb = new StringBuilder(n * 3 + 12);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
        }
        if (data.length > n) {
            sb.append(" …(共").append(data.length).append("B)");
        }
        return sb.toString();
    }
}
