package com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 同声传译进入、退出命令（协议 EC DB）。
 * <p>
 * 进入：99 EC DB 00 01 01 12 34
 * 退出：99 EC DB 00 01 00 12 34
 * 查询见 {@link SimultaneousInterpretationQueryCommand}（payload 02）。
 * <p>
 * 响应：99 EC DB 00 01 00 12 34（成功）/ 99 EC DB 00 01 01 12 34（失败）
 */
public class SimultaneousInterpretationCommand extends Command {
    private static final String TAG = "SimultaneousInterpretationCommand";

    private static final String COMMAND_PREFIX = "99ECDB";
    private static final String COMMAND_ENTER = "99ECDB000101";
    private static final String COMMAND_EXIT = "99ECDB000100";
    private static final String COMMAND_SUFFIX = "1234";

    /** 响应帧最小长度（chars）：99ECDB(6) + 0001(4) + payload(2) + 1234(4) = 16 */
    private static final int MIN_RESPONSE_LENGTH = 16;

    private final boolean enter;

    /**
     * @param enter true=进入同声传译，false=退出
     */
    public SimultaneousInterpretationCommand(boolean enter) {
        super();
        this.enter = enter;
        setCommandPrefix(COMMAND_PREFIX);
    }

    public boolean isEnter() {
        return enter;
    }

    @Override
    public String getCommandData() {
        String command = (enter ? COMMAND_ENTER : COMMAND_EXIT) + COMMAND_SUFFIX;
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
        String action = enter ? "进入" : "退出";

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
        String desc = action + "同声传译" + (success ? "成功" : "失败");
        if (success) {
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, status, desc);
        }
        String failDesc = desc + "，状态=0x" + String.format("%02X", status);
        HsLog.w(TAG, failDesc);
        return completed(ResultCode.FAILED, status, failDesc);
    }
}
