package com.yovo.biz_ggec_hs.internal.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 首页 V12 Sec 设置快照命令。
 *
 * <p>发送 {@code 99 EC C8 00 01 00 12 34}，设备以单个标准帧返回
 * {@code Type(1B) + Length(1B) + Value(Length B)} TLV 列表。</p>
 *
 * <p>固件字段：82 / 84 / 85 / 87 / 88 / 89 / 8A / B7 / B9 / CA。</p>
 */
public final class HomePageStatusV12SecCommand extends Command {
    private static final String TAG = "HomeStatusV12Sec";
    private static final String COMMAND_PREFIX = "99ECC8";
    private static final String COMMAND_DATA = COMMAND_PREFIX + "0001001234";

    private static final int TYPE_VOLUME_ADAPTIVE = 0x82;
    private static final int TYPE_PROTECT_EAR = 0x84;
    private static final int TYPE_KEY_EVENT = 0x85;
    private static final int TYPE_WEAR_DETECTION = 0x87;
    private static final int TYPE_VOICE_WAKEUP = 0x88;
    private static final int TYPE_GAME_MODE = 0x89;
    private static final int TYPE_FALL_ALERT = 0x8A;
    private static final int TYPE_LONG_TOUCH = 0xB7;
    private static final int TYPE_SHORT_TOUCH = 0xB9;
    private static final int TYPE_DOUBLE_TOUCH = 0xCA;

    public HomePageStatusV12SecCommand() {
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return isValidTlvFrame(responseData);
    }

    @Override
    public Response parseResponse(String responseData) {
        if (!isValidTlvFrame(responseData)) {
            return completed(ResultCode.FAILED, null, "首页 V12 Sec 响应格式错误");
        }

        try {
            Map<String, String> result = parsePayload(
                    responseData.substring(10, responseData.length() - 4));
            if (result.isEmpty()) {
                return completed(ResultCode.FAILED, null, "首页 V12 Sec 没有可用状态");
            }
            return completed(ResultCode.SUCCESS, result, "获取首页 V12 Sec 状态成功");
        } catch (IllegalArgumentException e) {
            HsLog.e(TAG, "解析首页 V12 Sec 响应失败", e);
            return completed(ResultCode.FAILED, null, "首页 V12 Sec 状态解析失败");
        }
    }

    static Map<String, String> parsePayload(String payload) {
        if (!isValidTlvPayload(payload)) {
            throw new IllegalArgumentException("TLV 数据不完整");
        }

        Map<String, String> result = new LinkedHashMap<>();
        int offset = 0;
        while (offset < payload.length()) {
            int type = parseByte(payload, offset);
            int valueLength = parseByte(payload, offset + 2);
            int valueStart = offset + 4;
            String valueHex = payload.substring(valueStart, valueStart + valueLength * 2);
            parseTlv(type, valueHex, result);
            offset = valueStart + valueLength * 2;
        }
        return result;
    }

    private static void parseTlv(int type,
                                 String valueHex,
                                 Map<String, String> result) {
        switch (type) {
            case TYPE_VOLUME_ADAPTIVE:
            case TYPE_WEAR_DETECTION:
            case TYPE_VOICE_WAKEUP:
            case TYPE_GAME_MODE:
            case TYPE_FALL_ALERT:
                putBoolean(type, valueHex, result);
                break;
            case TYPE_PROTECT_EAR:
                parseProtectEar(valueHex, result);
                break;
            case TYPE_KEY_EVENT:
                parseKeyEvent(valueHex, result);
                break;
            case TYPE_LONG_TOUCH:
                parseTouchPair(valueHex, "B7", result);
                break;
            case TYPE_SHORT_TOUCH:
                parseTouchPair(valueHex, "B9", result);
                break;
            case TYPE_DOUBLE_TOUCH:
                parseTouchPair(valueHex, "CA", result);
                break;
            default:
                HsLog.d(TAG, String.format(Locale.US, "忽略未知 C8 TLV: %02X", type));
                break;
        }
    }

    private static void parseProtectEar(String valueHex, Map<String, String> result) {
        requireByteLength(valueHex, 2, "听力保护");
        putBooleanKey("84_0", valueHex.substring(0, 2), result);
        // 84_1（使用时长提醒）仅对内校验，不对外暴露
        Boolean usageRemind = ProtocolValues.parseBooleanByte(valueHex.substring(2, 4));
        if (usageRemind == null) {
            throw new IllegalArgumentException("84_1 布尔值无效: " + valueHex.substring(2, 4));
        }
        // 兼容旧首页键：84 取音量保护位
        result.put("84", result.get("84_0"));
    }

    private static void parseKeyEvent(String valueHex, Map<String, String> result) {
        // V12: L双击 / R双击 / L三击 / R三击
        requireByteLength(valueHex, 4, "触控按键");
        for (int index = 0; index < 4; index++) {
            result.put("85_" + index,
                    valueHex.substring(index * 2, index * 2 + 2).toUpperCase(Locale.US));
        }
    }

    private static void parseTouchPair(String valueHex,
                                       String prefix,
                                       Map<String, String> result) {
        requireByteLength(valueHex, 2, prefix);
        result.put(prefix + "_0", valueHex.substring(0, 2).toUpperCase(Locale.US));
        result.put(prefix + "_1", valueHex.substring(2, 4).toUpperCase(Locale.US));
    }

    private static void putBoolean(int type,
                                   String valueHex,
                                   Map<String, String> result) {
        putBooleanKey(String.format(Locale.US, "%02X", type), valueHex, result);
    }

    private static void putBooleanKey(String key,
                                      String valueHex,
                                      Map<String, String> result) {
        requireByteLength(valueHex, 1, key);
        Boolean value = ProtocolValues.parseBooleanByte(valueHex);
        if (value == null) {
            throw new IllegalArgumentException(key + " 布尔值无效: " + valueHex);
        }
        result.put(key, value ? "01" : "00");
    }

    private static boolean isValidTlvFrame(String responseData) {
        return ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                && isValidTlvPayload(responseData.substring(10, responseData.length() - 4));
    }

    private static boolean isValidTlvPayload(String payload) {
        if (payload == null || payload.isEmpty() || (payload.length() % 2) != 0) {
            return false;
        }
        int offset = 0;
        while (offset < payload.length()) {
            if (offset + 4 > payload.length()) {
                return false;
            }
            int valueLength;
            try {
                if (Character.digit(payload.charAt(offset), 16) < 0
                        || Character.digit(payload.charAt(offset + 1), 16) < 0) {
                    return false;
                }
                valueLength = Integer.parseInt(payload.substring(offset + 2, offset + 4), 16);
            } catch (NumberFormatException e) {
                return false;
            }
            int next = offset + 4 + valueLength * 2;
            if (valueLength == 0 || next > payload.length()) {
                return false;
            }
            for (int index = offset + 4; index < next; index++) {
                if (Character.digit(payload.charAt(index), 16) < 0) {
                    return false;
                }
            }
            offset = next;
        }
        return offset == payload.length();
    }

    private static int parseByte(String value, int offset) {
        return Integer.parseInt(value.substring(offset, offset + 2), 16);
    }

    private static void requireByteLength(String valueHex,
                                          int expectedLength,
                                          String fieldName) {
        if (valueHex.length() != expectedLength * 2) {
            throw new IllegalArgumentException(
                    fieldName + "长度错误: " + (valueHex.length() / 2));
        }
    }
}
