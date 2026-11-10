package com.yovo.biz_ggec_hs.internal.protocol;

import java.util.Locale;

/**
 * 协议基础值的严格解析工具。
 */
public final class ProtocolValues {
    private ProtocolValues() {
    }

    /**
     * 只接受协议布尔值 00 和 01，其他值返回 null。
     */
    public static Boolean parseBooleanByte(String value) {
        if ("00".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        if ("01".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        return null;
    }

    public static boolean isFixedFrame(String frame, String prefix, int length) {
        return frame != null
                && prefix != null
                && length >= 4
                && prefix.length() <= length
                && frame.length() == length
                && isHex(frame)
                && frame.regionMatches(true, 0, prefix, 0, prefix.length())
                && frame.regionMatches(true, frame.length() - 4, "1234", 0, 4);
    }

    /**
     * 校验标准命令帧：命令前缀、两字节载荷长度、载荷和固定序列尾标必须完整一致。
     */
    public static boolean isCompleteFrame(String frame, String commandPrefix) {
        if (frame == null
                || commandPrefix == null
                || commandPrefix.length() != 6
                || frame.length() < 14
                || !isHex(frame)
                || !frame.regionMatches(true, 0, commandPrefix, 0, commandPrefix.length())
                || !frame.regionMatches(true, frame.length() - 4, "1234", 0, 4)) {
            return false;
        }
        try {
            int payloadLength = Integer.parseInt(frame.substring(6, 10), 16);
            return frame.length() == 14 + payloadLength * 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isStandardFrame(String frame) {
        return frame != null
                && frame.length() >= 14
                && (frame.length() & 1) == 0
                && isCompleteFrame(frame, frame.substring(0, 6));
    }

    /**
     * 校验设备上行帧。绝大多数命令使用字节长度；组合电量上报的长度字段按电池条目数计，
     * 是协议中已知且受边界约束的例外。
     */
    public static boolean isProtocolFrame(String frame) {
        return isStandardFrame(frame) || isCombinedBatteryFrame(frame);
    }

    private static boolean isCombinedBatteryFrame(String frame) {
        return frame != null
                && frame.length() == 26
                && isHex(frame)
                && frame.regionMatches(true, 0, "99EC800003", 0, 10)
                && frame.regionMatches(true, frame.length() - 4, "1234", 0, 4);
    }

    public static boolean isHex(String value) {
        if (value == null || value.isEmpty() || (value.length() & 1) != 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeMacAddress(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace(":", "").replace("-", "");
        if (!normalized.matches("[0-9a-fA-F]{12}")) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
