package com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd;

import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 自定义 EQ 载荷：10 个频段增益 + UTF-8 名称。
 *
 * <p>C7 的 B2 TLV 与独立 B2 查询共用。增益合法范围 {@code 0x00~0x18}。
 * 若第 10 段（16K）越界且像名称首字节（例如 {@code IOS} 的 {@code 'I'}=73），
 * 则按原始字节收回名称，16K 回填 0 dB，避免 App 落库出「OS + 超范围」。</p>
 */
public final class CustomEqPayload {
    private static final String TAG = "CustomEqPayload";
    private static final int EMPTY_GAIN = 0xFF;

    public final int[] gains;
    public final String name;

    private CustomEqPayload(int[] gains, String name) {
        this.gains = gains;
        this.name = name != null ? name : "";
    }

    public static CustomEqPayload empty() {
        return new CustomEqPayload(new int[0], "");
    }

    public boolean isEmpty() {
        return gains.length == 0;
    }

    public String gainsCsv() {
        if (gains.length == 0) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < gains.length; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(gains[index]);
        }
        return text.toString();
    }

    /**
     * @param valueHex 不含 Type/Length 的 value：10 字节增益 + 名称字节
     */
    public static CustomEqPayload parseValue(String valueHex) {
        if (valueHex == null
                || valueHex.length() < CustomEQRealtimeCommand.BAND_COUNT * 2
                || (valueHex.length() % 2) != 0) {
            throw new IllegalArgumentException("自定义 EQ 数据长度不足");
        }

        int[] gains = new int[CustomEQRealtimeCommand.BAND_COUNT];
        for (int index = 0; index < CustomEQRealtimeCommand.BAND_COUNT; index++) {
            gains[index] = parseByte(valueHex, index * 2);
        }
        byte[] nameBytes = hexToBytes(
                valueHex.substring(CustomEQRealtimeCommand.BAND_COUNT * 2));

        if (isEmptyMarker(gains)) {
            return empty();
        }
        if (allGainsInRange(gains)) {
            return new CustomEqPayload(gains, decodeUtf8(nameBytes));
        }

        CustomEqPayload recovered = tryRecoverNameEatenAsLastGain(gains, nameBytes);
        if (recovered != null) {
            HsLog.w(TAG, "自定义 EQ 16K 增益越界，已按名称首字节回收: "
                    + recovered.name + " gains=" + recovered.gainsCsv());
            return recovered;
        }
        throw new IllegalArgumentException("自定义 EQ 增益值超出协议范围");
    }

    private static CustomEqPayload tryRecoverNameEatenAsLastGain(int[] gains, byte[] nameTail) {
        if (nameTail == null || nameTail.length == 0) {
            return null;
        }
        for (int index = 0; index < CustomEQRealtimeCommand.BAND_COUNT - 1; index++) {
            if (!isGainInRange(gains[index])) {
                return null;
            }
        }
        int eaten = gains[CustomEQRealtimeCommand.BAND_COUNT - 1];
        if (isGainInRange(eaten) || !isNameLeadingByte(eaten)) {
            return null;
        }
        int[] fixed = Arrays.copyOf(gains, gains.length);
        fixed[CustomEQRealtimeCommand.BAND_COUNT - 1] = CustomEQRealtimeCommand.GAIN_NEUTRAL;
        byte[] recoveredNameBytes = new byte[1 + nameTail.length];
        recoveredNameBytes[0] = (byte) eaten;
        System.arraycopy(nameTail, 0, recoveredNameBytes, 1, nameTail.length);
        return new CustomEqPayload(fixed, decodeUtf8(recoveredNameBytes));
    }

    private static boolean isEmptyMarker(int[] gains) {
        for (int gain : gains) {
            if (gain != EMPTY_GAIN) {
                return false;
            }
        }
        return true;
    }

    private static boolean allGainsInRange(int[] gains) {
        for (int gain : gains) {
            if (!isGainInRange(gain)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGainInRange(int gain) {
        return gain >= CustomEQRealtimeCommand.GAIN_MIN
                && gain <= CustomEQRealtimeCommand.GAIN_MAX;
    }

    private static boolean isNameLeadingByte(int value) {
        return (value >= 0x20 && value <= 0x7E)
                || (value >= 0xC2 && value <= 0xF4);
    }

    private static int parseByte(String value, int offset) {
        return Integer.parseInt(value.substring(offset, offset + 2), 16);
    }

    private static byte[] hexToBytes(String valueHex) {
        byte[] bytes = new byte[valueHex.length() / 2];
        for (int offset = 0; offset < valueHex.length(); offset += 2) {
            bytes[offset / 2] = (byte) parseByte(valueHex, offset);
        }
        return bytes;
    }

    private static String decodeUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
