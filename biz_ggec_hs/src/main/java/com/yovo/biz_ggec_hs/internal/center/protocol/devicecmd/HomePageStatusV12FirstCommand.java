package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEqPayload;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 首页 V12 First 状态命令。
 *
 * <p>发送 {@code 99 EC C7 00 01 00 12 34}，设备以单个标准帧返回
 * {@code Type(1B) + Length(1B) + Value(Length B)} TLV 列表。</p>
 */
public final class HomePageStatusV12FirstCommand extends Command {
    private static final String TAG = "HomeStatusV12First";
    private static final String COMMAND_PREFIX = "99ECC7";
    private static final String COMMAND_DATA = COMMAND_PREFIX + "0001001234";

    private static final int TYPE_BATTERY = 0x80;
    private static final int TYPE_SOUND_QUALITY = 0x81;
    private static final int TYPE_EQ_MODE = 0x83;
    private static final int TYPE_WEAR_DETECTION = 0x87;
    private static final int TYPE_FIRMWARE = 0x94;
    private static final int TYPE_CASE_CHARGING = 0x95;
    private static final int TYPE_CUSTOM_EQ = 0xB2;
    private static final int TYPE_SERVICE_STATUS = 0xB5;

    public HomePageStatusV12FirstCommand() {
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
            return completed(ResultCode.FAILED, null, "首页 V12 First 响应格式错误");
        }

        try {
            Map<String, String> result = parsePayload(
                    responseData.substring(10, responseData.length() - 4));
            if (result.isEmpty()) {
                return completed(ResultCode.FAILED, null, "首页 V12 First 没有可用状态");
            }
            return completed(ResultCode.SUCCESS, result, "获取首页 V12 First 状态成功");
        } catch (IllegalArgumentException e) {
            HsLog.e(TAG, "解析首页 V12 First 响应失败", e);
            return completed(ResultCode.FAILED, null, "首页 V12 First 状态解析失败");
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
            case TYPE_BATTERY:
                parseBattery(valueHex, result);
                break;
            case TYPE_SOUND_QUALITY:
            case TYPE_WEAR_DETECTION:
            case TYPE_CASE_CHARGING:
                putBoolean(type, valueHex, result);
                break;
            case TYPE_EQ_MODE:
                requireByteLength(valueHex, 1, "EQ 模式");
                int eqMode = parseByte(valueHex, 0);
                if (eqMode > 5) {
                    throw new IllegalArgumentException("EQ 模式超出范围: " + eqMode);
                }
                result.put("83", valueHex.toUpperCase());
                break;
            case TYPE_FIRMWARE:
                if (valueHex.isEmpty()) {
                    throw new IllegalArgumentException("固件版本为空");
                }
                result.put("94", decodeUtf8(valueHex));
                break;
            case TYPE_CUSTOM_EQ:
                parseCustomEq(valueHex, result);
                break;
            case TYPE_SERVICE_STATUS:
                parseServiceStatus(valueHex, result);
                break;
            default:
                HsLog.d(TAG, String.format("忽略未知 C7 TLV: %02X", type));
                break;
        }
    }

    private static void parseBattery(String valueHex, Map<String, String> result) {
        requireByteLength(valueHex, 6, "电量");
        for (int offset = 0; offset < valueHex.length(); offset += 4) {
            int batteryType = parseByte(valueHex, offset);
            int level = parseByte(valueHex, offset + 2);
            if (batteryType < 1 || batteryType > 3) {
                throw new IllegalArgumentException("未知电量类型: " + batteryType);
            }
            result.put("80_" + batteryType,
                    level >= 1 && level <= 100 ? String.valueOf(level) : "--");
        }
    }

    private static void putBoolean(int type,
                                   String valueHex,
                                   Map<String, String> result) {
        requireByteLength(valueHex, 1, String.format("%02X", type));
        Boolean value = ProtocolValues.parseBooleanByte(valueHex);
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("%02X 布尔值无效: %s", type, valueHex));
        }
        result.put(String.format("%02X", type), value ? "01" : "00");
    }

    private static void parseCustomEq(String valueHex, Map<String, String> result) {
        try {
            CustomEqPayload payload = CustomEqPayload.parseValue(valueHex);
            if (payload.isEmpty()) {
                return;
            }
            result.put("B2_GAINS", payload.gainsCsv());
            result.put("B2_NAME", payload.name);
        } catch (IllegalArgumentException e) {
            HsLog.e(TAG, "忽略无效 C7 B2 自定义 EQ，保留其余首页状态", e);
        }
    }

    private static void parseServiceStatus(String valueHex,
                                           Map<String, String> result) {
        int byteCount = valueHex == null ? 0 : valueHex.length() / 2;
        if (byteCount != 3 && byteCount != 6 && byteCount != 7) {
            throw new IllegalArgumentException("服务状态长度无效: " + byteCount);
        }
        for (int index = 0; index < byteCount; index++) {
            int status = parseByte(valueHex, index * 2);
            if (!isValidServiceStatus(index, status)) {
                throw new IllegalArgumentException("服务状态值无效: index="
                        + index + " status=" + status);
            }
            result.put("B5_" + index, String.valueOf(status));
        }
    }

    /**
     * 与 {@code ServiceStatusCommand} 对齐：录音允许 0~2，佩戴允许 0~3，
     * AI 翻译允许 0~4，其余位只允许 0/1。
     */
    private static boolean isValidServiceStatus(int index, int status) {
        if (index == 2) {
            return status >= 0 && status <= 2;
        }
        if (index == 5) {
            return status >= 0 && status <= 3;
        }
        if (index == 6) {
            return status >= 0 && status <= 4;
        }
        return status == 0 || status == 1;
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

    private static String decodeUtf8(String valueHex) {
        byte[] bytes = new byte[valueHex.length() / 2];
        for (int offset = 0; offset < valueHex.length(); offset += 2) {
            bytes[offset / 2] = (byte) parseByte(valueHex, offset);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
