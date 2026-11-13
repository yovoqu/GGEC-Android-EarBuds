package com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 单路电量查询的公共协议实现。
 */
abstract class AbstractBatteryLevelCommand extends Command {
    private static final String TAG = "BatteryLevelCommand";
    private static final String COMMAND_PREFIX = "99EC80";
    private static final String COMMAND_SUFFIX = "1234";
    private static final int RESPONSE_LENGTH = 18;

    private final String batteryName;
    private final String commandData;
    private final String responsePrefix;
    private String batteryLevel = "--";

    AbstractBatteryLevelCommand(int batteryId, String batteryName) {
        if (batteryId < 1 || batteryId > 3) {
            throw new IllegalArgumentException("电池编号必须在 1 到 3 之间");
        }
        this.batteryName = batteryName;
        String idHex = String.format("%02X", batteryId);
        commandData = COMMAND_PREFIX + "0001" + idHex + COMMAND_SUFFIX;
        responsePrefix = COMMAND_PREFIX + "0002" + idHex;
        setCommandPrefix(COMMAND_PREFIX);
    }

    public final String getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public final String getCommandData() {
        return commandData;
    }

    @Override
    public final boolean isResponseMatch(String responseData) {
        return hasExpectedFrameShape(responseData);
    }

    @Override
    public final Response parseResponse(String responseData) {
        if (!hasExpectedFrameShape(responseData)) {
            batteryLevel = "--";
            return completed(
                    ResultCode.FAILED, batteryLevel, batteryName + "电量响应格式错误");
        }
        try {
            int value = Integer.parseInt(responseData.substring(12, 14), 16);
            if (value < 1 || value > 100) {
                batteryLevel = "--";
                HsLog.w(TAG, batteryName + "电量超出有效范围: " + value);
                return completed(
                        ResultCode.FAILED, batteryLevel, batteryName + "电量超出有效范围");
            }
            batteryLevel = String.valueOf(value);
            return completed(
                    ResultCode.SUCCESS, batteryLevel, "成功获取" + batteryName + "电量");
        } catch (NumberFormatException e) {
            batteryLevel = "--";
            HsLog.e(TAG, "解析" + batteryName + "电量失败", e);
            return completed(
                    ResultCode.FAILED, batteryLevel, "解析" + batteryName + "电量失败");
        }
    }

    private boolean hasExpectedFrameShape(String responseData) {
        return responseData != null
                && responseData.length() == RESPONSE_LENGTH
                && responseData.regionMatches(true, 0, responsePrefix, 0, responsePrefix.length())
                && responseData.regionMatches(
                        true,
                        responseData.length() - COMMAND_SUFFIX.length(),
                        COMMAND_SUFFIX,
                        0,
                        COMMAND_SUFFIX.length());
    }
}
