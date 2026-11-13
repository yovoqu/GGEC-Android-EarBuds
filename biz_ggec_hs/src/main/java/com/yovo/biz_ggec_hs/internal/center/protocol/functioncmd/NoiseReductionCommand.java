package com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 降噪算法开关命令（协议 EC DC）。
 * <p>
 * 开启：99 EC DC 00 01 01 12 34
 * 关闭：99 EC DC 00 01 00 12 34
 * <p>
 * 响应：99 EC DC 00 01 00 12 34（成功）/ 99 EC DC 00 01 01 12 34（失败）
 */
public class NoiseReductionCommand extends Command {
    private static final String TAG = "NoiseReductionCommand";

    private static final String COMMAND_PREFIX = "99ECDC";
    private static final String COMMAND_ON = "99ECDC000101";
    private static final String COMMAND_OFF = "99ECDC000100";
    private static final String COMMAND_SUFFIX = "1234";

    /** 响应帧最小长度（chars）：99ECDC(6) + 0001(4) + payload(2) + 1234(4) = 16 */
    private static final int MIN_RESPONSE_LENGTH = 16;

    private final boolean enabled;

    /**
     * @param enabled true=开启降噪算法，false=关闭
     */
    public NoiseReductionCommand(boolean enabled) {
        super();
        this.enabled = enabled;
        setCommandPrefix(COMMAND_PREFIX);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getCommandData() {
        String command = (enabled ? COMMAND_ON : COMMAND_OFF) + COMMAND_SUFFIX;
        HsLog.d(TAG, "生成的命令: " + command);
        return command;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);
        String action = enabled ? "开启" : "关闭";

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() < MIN_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 1, "响应数据格式错误");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 1, "状态字节解析失败");
        }

        boolean success = status == 0x00;
        String desc = action + "降噪算法" + (success ? "成功" : "失败");
        if (success) {
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, status, desc);
        }
        String failDesc = desc + "，状态=0x" + String.format("%02X", status);
        HsLog.w(TAG, failDesc);
        return completed(ResultCode.FAILED, status, failDesc);
    }
}
