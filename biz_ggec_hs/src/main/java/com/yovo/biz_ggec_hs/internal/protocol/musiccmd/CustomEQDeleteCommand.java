package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;


/**
 * 删除保存的耳机自定义EQ模式参数
 * 发送：99 EC B3 00 01 00 12 34
 * 响应：99 EC B3 00 01 00 12 34
 *   status: 0x00=成功, 0x01=删除失败
 */
public class CustomEQDeleteCommand extends Command {
    private static final String TAG = "CustomEQDeleteCmd";
    private static final String COMMAND_PREFIX_STR = "99ECB3";
    private static final String COMMAND_SUFFIX = "1234";

    public CustomEQDeleteCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX_STR);
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX_STR + "0001" + "00" + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应: " + responseData);
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX_STR)
                || responseData.length() != 16) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        try {
            int status = Integer.parseInt(responseData.substring(10, 12), 16);
            boolean isSuccess = (status == 0x00);
            String result = isSuccess ? "自定义EQ删除成功" : "自定义EQ删除失败";
            return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, status, result);
        } catch (Exception e) {
            HsLog.e(TAG, "解析删除响应失败", e);
            return completed(ResultCode.FAILED, 0, "解析删除响应失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.length() >= 6
                && COMMAND_PREFIX_STR.equals(responseData.substring(0, 6));
    }
}
