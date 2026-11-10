package com.yovo.biz_ggec_hs.internal.protocol.controlcmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 将全部触控功能恢复为出厂默认值。
 */
public final class ResetControlCommand extends Command {
    private static final String TAG = "ResetControlCommand";
    private static final String COMMAND_PREFIX = "99EC860001";
    private static final String COMMAND_DATA = "99EC860001001234";

    public ResetControlCommand() {
        setCommandPrefix("99EC86");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);
        boolean success = responseData != null
                && COMMAND_DATA.equalsIgnoreCase(responseData);
        return completed(
                success ? ResultCode.SUCCESS : ResultCode.FAILED,
                success ? 1 : 0,
                success ? "恢复默认控制设置成功" : "恢复默认控制设置失败");
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }
}
