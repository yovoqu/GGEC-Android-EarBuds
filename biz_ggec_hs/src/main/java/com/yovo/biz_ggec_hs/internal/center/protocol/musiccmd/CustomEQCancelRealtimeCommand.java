package com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 取消实时设置耳机自定义EQ模式参数
 * 协议：99 EC B4 00 01 00 12 34
 */
public class CustomEQCancelRealtimeCommand extends Command {
    private static final String TAG = "CustomEQCancelRTCmd";
    private static final String COMMAND_PREFIX_STR = "99ECB4";
    private static final String COMMAND_DATA = "99ECB40001001234";

    public CustomEQCancelRealtimeCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX_STR);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应: " + responseData);
        if (responseData == null || !COMMAND_DATA.equalsIgnoreCase(responseData)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }
        return completed(ResultCode.SUCCESS, 0, "取消自定义EQ实时设置成功");
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.length() >= 6
                && COMMAND_PREFIX_STR.equals(responseData.substring(0, 6));
    }
}
