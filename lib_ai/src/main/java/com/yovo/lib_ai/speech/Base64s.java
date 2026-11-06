package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

/**
 * 最小 base64 解码器。
 *
 * <p>不用 {@code java.util.Base64}：那是 API 26 才有的，而模块 minSdk 是 24；
 * {@code android.util.Base64} 在单元测试里是抛异常的桩，测不了。这段代码不依赖任何平台 API。
 */
final class Base64s {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Base64s() {
    }

    /**
     * 解标准 base64（容忍空白与换行，忽略填充位置的合法性由长度约束保证）。
     *
     * @throws IllegalArgumentException 长度不是 4 的倍数或出现字母表外字符
     */
    @NonNull
    static byte[] decode(@NonNull String text) {
        String clean = stripWhitespace(text);
        if (clean.isEmpty()) {
            return new byte[0];
        }
        if (clean.length() % 4 != 0) {
            throw new IllegalArgumentException("base64 length is not a multiple of 4");
        }
        int padding = 0;
        if (clean.charAt(clean.length() - 1) == '=') {
            padding++;
            if (clean.length() >= 2 && clean.charAt(clean.length() - 2) == '=') {
                padding++;
            }
        }
        int payloadEnd = clean.length() - padding;
        byte[] out = new byte[(clean.length() / 4) * 3 - padding];
        int outIndex = 0;
        for (int base = 0; base < clean.length(); base += 4) {
            int chunk = 0;
            for (int offset = 0; offset < 4; offset++) {
                int position = base + offset;
                char symbol = clean.charAt(position);
                int value;
                if (position >= payloadEnd) {
                    if (symbol != '=') {
                        throw new IllegalArgumentException("illegal base64 padding position");
                    }
                    value = 0;
                } else {
                    value = alphabetValue(symbol);
                    if (value < 0) {
                        throw new IllegalArgumentException("illegal base64 character '" + symbol + "'");
                    }
                }
                chunk = (chunk << 6) | value;
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) ((chunk >> 16) & 0xFF);
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) ((chunk >> 8) & 0xFF);
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) (chunk & 0xFF);
            }
        }
        return out;
    }

    @NonNull
    private static String stripWhitespace(@NonNull String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char symbol = value.charAt(index);
            if (symbol != ' ' && symbol != '\n' && symbol != '\r' && symbol != '\t') {
                out.append(symbol);
            }
        }
        return out.toString();
    }

    private static int alphabetValue(char symbol) {
        if (symbol >= 'A' && symbol <= 'Z') {
            return symbol - 'A';
        }
        if (symbol >= 'a' && symbol <= 'z') {
            return symbol - 'a' + 26;
        }
        if (symbol >= '0' && symbol <= '9') {
            return symbol - '0' + 52;
        }
        if (symbol == '+') {
            return 62;
        }
        if (symbol == '/') {
            return 63;
        }
        return -1;
    }
}
