package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * 全局状态多帧聚合命令。
 *
 * 只有结构完整的主状态、电池和充电状态帧才能推进聚合清单。主状态包和电池包是必需项，
 * 充电状态包是可选项。所有超时均由 CommandScheduler 统一管理。
 */
public class GlobalStatusCommand extends Command {
    private static final String TAG = "GlobalStatusCommand";

    // 命令前缀
    private static final String PREFIX_GLOBAL_STATUS = "99EC98";
    private static final String PREFIX_BATTERY = "99EC80";
    private static final String PREFIX_UNKNOWN_95 = "99EC95";

    // 发送命令数据
    private static final String COMMAND_DATA = "99EC98000100" + "1234";

    // 功能类型标识 (public, 供外部解析器使用)
    public static final byte TYPE_WEAR_DETECTION = (byte) 0x87;  // 佩戴检测
    public static final byte TYPE_VOICE_WAKEUP = (byte) 0x88;    // 语音控制
    public static final byte TYPE_GAME_MODE = (byte) 0x89;       // 游戏模式
    public static final byte TYPE_FALL_ALERT = (byte) 0x8A;      // 掉落提醒
    public static final byte TYPE_SOUND_QUALITY = (byte) 0x81;   // 音质模式
    public static final byte TYPE_VOLUME_ADAPTIVE = (byte) 0x82; // 音量自适应
    public static final byte TYPE_EQ_MODE = (byte) 0x83;         // EQ模式
    public static final byte TYPE_VOLUME_REMIND = (byte) 0x84;   // 听力保护/音量提醒
    public static final byte TYPE_KEY_CONTROL = (byte) 0x85;     // 按键控制
    public static final byte TYPE_FIRMWARE = (byte) 0x94;        // 固件版本


    public enum AggregationState {IDLE, WAITING_FOR_FIRST_PACKET, AGGREGATING, FINISHED}

    private AggregationState currentState = AggregationState.IDLE;

    // 清单，用于跟踪待接收的数据包类型
    private final Set<String> manifest = new HashSet<>();
    private final Map<String, String> dictResult = new HashMap<>();

    private static final long INITIAL_TIMEOUT_MS = 3000;
    private static final long INTER_PACKET_TIMEOUT_MS = 2000;
    private static final long FINAL_MAIN_PACKET_TIMEOUT_MS = 2000;

    private boolean waitingForFinalMainPacket;

    /**
     * 构造方法
     */
    public GlobalStatusCommand() {
        super();
        setCommandPrefix(PREFIX_GLOBAL_STATUS); // 主命令前缀
    }

    /**
     * 获取当前聚合状态
     *
     * @return 当前聚合状态
     */
    public AggregationState getCurrentState() {
        return currentState;
    }

    @Override
    public String getCommandData() {
        // 1. 重置所有状态
        this.currentState = AggregationState.WAITING_FOR_FIRST_PACKET;
        this.dictResult.clear();
        this.waitingForFinalMainPacket = false;

        // 2. 初始化清单，定义我们期望收到的所有数据包类型
        this.manifest.clear();
        this.manifest.add(PREFIX_GLOBAL_STATUS);
        this.manifest.add(PREFIX_BATTERY);
        this.manifest.add(PREFIX_UNKNOWN_95);

        return COMMAND_DATA;
    }

    /**
     * 仅当命令未终结时，才匹配响应
     */
    @Override
    public boolean isResponseMatch(String responseData) {
        if (currentState == AggregationState.FINISHED || responseData == null) {
            return false;
        }
        return isValidGlobalStatusPacket(responseData)
                || isValidBatteryPacket(responseData)
                || isValidChargingPacket(responseData);
    }

    /**
     * 解析响应并更新清单。如果清单完成，则立即结束命令。
     */
    @Override
    public Response parseResponse(String responseData) {
        if (currentState == AggregationState.FINISHED) {
            return completed(ResultCode.FAILED, "命令已完成，忽略后续数据包");
        }

        // 收到任何包都意味着设备有响应，切换到聚合状态
        if (currentState == AggregationState.WAITING_FOR_FIRST_PACKET) {
            currentState = AggregationState.AGGREGATING;
        }

        HsLog.d(TAG, "收到数据包: " + responseData);

        if (startsWith(responseData, PREFIX_GLOBAL_STATUS)) {
            dictResult.putAll(parseGlobalStatusPacket(responseData));
            manifest.remove(PREFIX_GLOBAL_STATUS);
            waitingForFinalMainPacket = false;
        } else if (startsWith(responseData, PREFIX_BATTERY)) {
            dictResult.putAll(parseBatteryPacket(responseData));
            manifest.remove(PREFIX_BATTERY);
        } else if (startsWith(responseData, PREFIX_UNKNOWN_95)) {
            dictResult.putAll(parse95Packet(responseData));
            manifest.remove(PREFIX_UNKNOWN_95);
        }

        // 检查清单是否已全部完成
        if (manifest.isEmpty()) {
            HsLog.i(TAG, "全局状态所需数据包已收齐");
            return finish(ResultCode.SUCCESS, "聚合成功");
        }
        HsLog.d(TAG, "清单未完成，等待剩余项: " + manifest);
        return Response.pending();
    }

    @Override
    public long getTimeoutMs() {
        return INITIAL_TIMEOUT_MS;
    }

    @Override
    public long getInterFrameTimeoutMs() {
        return waitingForFinalMainPacket
                ? FINAL_MAIN_PACKET_TIMEOUT_MS
                : INTER_PACKET_TIMEOUT_MS;
    }

    @Override
    public Set<String> getResponseFamilies() {
        return new LinkedHashSet<>(Arrays.asList(
                PREFIX_GLOBAL_STATUS, PREFIX_BATTERY, PREFIX_UNKNOWN_95));
    }

    @Override
    public Response onResponseTimeout() {
        if (currentState == AggregationState.WAITING_FOR_FIRST_PACKET) {
            HsLog.e(TAG, "全局状态初始等待超时，未收到任何数据包");
            return finish(ResultCode.TIMEOUT, "获取状态超时，设备无响应");
        }
        if (manifest.isEmpty()) {
            return finish(ResultCode.SUCCESS, "聚合成功");
        }
        if (manifest.size() == 1 && manifest.contains(PREFIX_UNKNOWN_95)) {
            HsLog.d(TAG, "可选充电状态包超时，使用已收到的数据完成聚合");
            return finish(ResultCode.SUCCESS, "聚合成功");
        }
        if (manifest.size() == 1 && manifest.contains(PREFIX_GLOBAL_STATUS)
                && !waitingForFinalMainPacket) {
            waitingForFinalMainPacket = true;
            HsLog.d(TAG, "快速状态包已收齐，继续等待主状态包");
            return Response.pending();
        }
        HsLog.w(TAG, "全局状态聚合超时，缺失项: " + manifest);
        return finish(ResultCode.TIMEOUT,
                manifest.contains(PREFIX_GLOBAL_STATUS)
                        ? "聚合失败，主状态包丢失"
                        : "聚合超时，数据不完整");
    }

    /**
     * 终结命令，整理并发送最终结果
     *
     * @param code    结果码
     * @param message 结果消息
     */
    private Response finish(int code, String message) {
        HsLog.d(TAG, "结束全局状态聚合: 结果码=" + code + "，说明=" + message);
        currentState = AggregationState.FINISHED;

        if (code == ResultCode.SUCCESS) {
            if (!dictResult.isEmpty()) {
                long nullCount = dictResult.values().stream().filter(v -> v == null).count();
                if (nullCount >= 2) {
                    HsLog.w(TAG, "部分状态解析失败（" + nullCount + "项），继续返回已解析结果");
                }
            }
            if (dictResult.isEmpty()) {
                HsLog.w(TAG, "聚合完成但没有解析出有效数据");
                return completed(ResultCode.FAILED, "无有效数据");
            }
            HsLog.i(TAG, "全局状态聚合完成，共 " + dictResult.size() + " 项");
            return completed(ResultCode.SUCCESS, new HashMap<>(dictResult), message);
        }
        return completed(code, message);
    }

    @Override
    protected void onAborted() {
        currentState = AggregationState.FINISHED;
    }


    /**
     * 解析全局状态数据包 (99EC98...)
     *
     * @param responseData 响应数据
     * @return 包含所有解析出的状态项的Map
     */
    public static Map<String, String> parseGlobalStatusPacket(String responseData) {
        Map<String, String> resultMap = new HashMap<>();
        if (!isValidGlobalStatusPacket(responseData)) {
            HsLog.w(TAG, "全局状态包结构无效");
            return resultMap;
        }

        try {
            int startIndex = PREFIX_GLOBAL_STATUS.length();
            String lengthHex = responseData.substring(startIndex, startIndex + 4);
            int dataLengthInBytes = Integer.parseInt(lengthHex, 16);
            int dataLengthInChars = dataLengthInBytes * 2;

            int dataStartIndex = startIndex + 4;
            int dataEndIndex = dataStartIndex + dataLengthInChars;

            String statusData = responseData.substring(dataStartIndex, dataEndIndex);
            parseAllStatusItems(statusData, resultMap);
        } catch (Exception e) {
            HsLog.e(TAG, "解析全局状态包失败", e);
        }
        return resultMap;
    }

    /**
     * 解析电池信息数据包 (99EC80...)
     *
     * @param responseData 响应数据
     * @return 包含电池信息的Map
     */
    public static Map<String, String> parseBatteryPacket(String responseData) {
        Map<String, String> resultMap = new HashMap<>();
        if (!isValidBatteryPacket(responseData)) {
            HsLog.w(TAG, "电池信息包结构无效");
            return resultMap;
        }
        String payload = responseData.substring(10, responseData.length() - 4);
        for (int offset = 0; offset < payload.length(); offset += 4) {
            int type = Integer.parseInt(payload.substring(offset, offset + 2), 16);
            int value = Integer.parseInt(payload.substring(offset + 2, offset + 4), 16);
            if (type < 1 || type > 3) {
                HsLog.w(TAG, "忽略未知电池类型: " + type);
                continue;
            }
            String batteryValue = value >= 1 && value <= 100 ? String.valueOf(value) : "--";
            if ("--".equals(batteryValue)) {
                HsLog.w(TAG, "电池电量超出有效范围: " + value);
            }
            resultMap.put(String.format("80_%d", type), batteryValue);
        }
        return resultMap;
    }

    /**
     * 解析未知的95类型数据包 (99EC95...)
     *
     * @param responseData 响应数据
     * @return 包含解析出的数据的Map
     */
    public static Map<String, String> parse95Packet(String responseData) {
        Map<String, String> resultMap = new HashMap<>();
        if (!isValidChargingPacket(responseData)) {
            HsLog.w(TAG, "充电状态包结构无效");
            return resultMap;
        }
        String valueHex = responseData.substring(10, 12);
        if (ProtocolValues.parseBooleanByte(valueHex) != null) {
            resultMap.put("95", valueHex);
        } else {
            HsLog.w(TAG, "充电状态值无效: " + valueHex);
        }
        return resultMap;
    }

    /**
     * 解析状态数据字符串，提取所有TLV格式的状态项，并填充到结果Map中
     *
     * @param statusData 状态数据字符串
     * @param resultMap  用于存储结果的Map
     */
    private static void parseAllStatusItems(String statusData, Map<String, String> resultMap) {
        int index = 0;
        while (index < statusData.length()) {
            try {
                String typeHex = statusData.substring(index, index + 2);
                String lengthHex = statusData.substring(index + 2, index + 4);
                byte type = (byte) Integer.parseInt(typeHex, 16);
                int length = Integer.parseInt(lengthHex, 16);
                String valueHex = statusData.substring(index + 4, index + 4 + length * 2);
                parseSingleStatusItem(type, valueHex, resultMap);
                index += 4 + length * 2;
            } catch (Exception e) {
                HsLog.e(TAG, "解析状态项失败", e);
                return;
            }
        }
    }

    /**
     * 解析单个状态项并将其放入结果Map
     *
     * @param type      状态类型
     * @param valueHex  状态值（十六进制字符串）
     * @param resultMap 用于存储结果的Map
     */
    private static void parseSingleStatusItem(byte type, String valueHex, Map<String, String> resultMap) {
        String typeKey = String.format("%02X", type);
        try {
            switch (type) {
                case TYPE_WEAR_DETECTION:
                case TYPE_VOICE_WAKEUP:
                case TYPE_GAME_MODE:
                case TYPE_FALL_ALERT:
                case TYPE_SOUND_QUALITY:
                case TYPE_VOLUME_ADAPTIVE:
                    putBooleanStatus(typeKey, valueHex, resultMap);
                    break;
                case TYPE_EQ_MODE:
                    resultMap.put(typeKey, valueHex);
                    break;
                case TYPE_VOLUME_REMIND:
                    putBooleanStatus(typeKey,
                            valueHex.length() >= 2
                                    ? valueHex.substring(valueHex.length() - 2)
                                    : valueHex,
                            resultMap);
                    break;
                case TYPE_KEY_CONTROL:
                    parseKeyControlStatus(valueHex, resultMap);
                    break;
                case TYPE_FIRMWARE:
                    StringBuilder output = new StringBuilder();
                    for (int i = 0; i < valueHex.length(); i += 2) {
                        String str = valueHex.substring(i, i + 2);
                        output.append((char) Integer.parseInt(str, 16));
                    }
                    resultMap.put(typeKey, output.toString());
                    break;
                default:
                    // 对于未知的类型，可以选择记录或忽略
                    HsLog.w(TAG, "发现未知的状态类型: " + typeKey);
                    break;
            }
        } catch (Exception e) {
            HsLog.e(TAG, "解析状态项失败: 类型=" + typeKey + ", 值=" + valueHex, e);
        }
    }

    private static void putBooleanStatus(String typeKey,
                                         String valueHex,
                                         Map<String, String> resultMap) {
        Boolean value = ProtocolValues.parseBooleanByte(valueHex);
        if (value == null) {
            HsLog.w(TAG, "状态值无效: 类型=" + typeKey + "，值=" + valueHex);
            return;
        }
        resultMap.put(typeKey, value ? "01" : "00");
    }

    /**
     * 解析 V11 EC98 KEY_EVENT(0x85)。
     * <p>
     * 固件 4 字节仍是 {@code [L双击][R双击][L三击][R三击]}。
     * V11 设置写入右耳槽（01/03），查询只回 {@code 85_0}=双击、{@code 85_1}=三击。
     * V12 左右耳四槽见 {@link HomePageStatusV12SecCommand}。
     *
     * @param keyControlData 按键控制数据（仅 value 段 hex）
     * @param resultMap      用于存储结果的Map
     */
    private static void parseKeyControlStatus(String keyControlData, Map<String, String> resultMap) {
        String key0 = String.format(Locale.US, "%02X_0", TYPE_KEY_CONTROL);
        String key1 = String.format(Locale.US, "%02X_1", TYPE_KEY_CONTROL);
        if (keyControlData == null || keyControlData.length() < 8) {
            HsLog.w(TAG, "按键控制数据格式不正确或长度不足: " + keyControlData);
            resultMap.put(key0, null);
            resultMap.put(key1, null);
            return;
        }
        try {
            String[] functions = ControlCommandV11.parseAggregateFunctions(keyControlData);
            resultMap.put(key0, functions[0]);
            resultMap.put(key1, functions[1]);
        } catch (Exception e) {
            HsLog.e(TAG, "解析按键控制状态失败: " + keyControlData, e);
            resultMap.put(key0, null);
            resultMap.put(key1, null);
        }
    }

    private static boolean isValidGlobalStatusPacket(String responseData) {
        if (!ProtocolValues.isCompleteFrame(responseData, PREFIX_GLOBAL_STATUS)) {
            return false;
        }
        return isValidTlvPayload(responseData.substring(10, responseData.length() - 4));
    }

    private static boolean isValidBatteryPacket(String responseData) {
        if (!hasEnvelope(responseData, PREFIX_BATTERY)) {
            return false;
        }
        String payload = responseData.substring(10, responseData.length() - 4);
        if (payload.isEmpty() || payload.length() % 4 != 0 || !isHex(payload)) {
            return false;
        }
        int declaredLength;
        try {
            declaredLength = Integer.parseInt(responseData.substring(6, 10), 16);
        } catch (NumberFormatException e) {
            return false;
        }
        int payloadBytes = payload.length() / 2;
        int recordCount = payload.length() / 4;
        return declaredLength == payloadBytes || declaredLength == recordCount;
    }

    private static boolean isValidChargingPacket(String responseData) {
        return ProtocolValues.isCompleteFrame(responseData, PREFIX_UNKNOWN_95)
                && responseData.length() == 16;
    }

    private static boolean isValidTlvPayload(String payload) {
        if (payload.isEmpty() || !isHex(payload)) {
            return false;
        }
        int offset = 0;
        while (offset < payload.length()) {
            if (offset + 4 > payload.length()) {
                return false;
            }
            int valueLength;
            try {
                valueLength = Integer.parseInt(payload.substring(offset + 2, offset + 4), 16);
            } catch (NumberFormatException e) {
                return false;
            }
            int next = offset + 4 + valueLength * 2;
            if (valueLength == 0 || next > payload.length()) {
                return false;
            }
            offset = next;
        }
        return offset == payload.length();
    }

    private static boolean hasEnvelope(String responseData, String prefix) {
        return responseData != null
                && responseData.length() >= 16
                && startsWith(responseData, prefix)
                && responseData.regionMatches(true, responseData.length() - 4, "1234", 0, 4);
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null
                && value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isHex(String value) {
        if (value == null || value.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
