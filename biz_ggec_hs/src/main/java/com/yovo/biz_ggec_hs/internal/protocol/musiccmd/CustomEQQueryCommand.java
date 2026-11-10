package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 查询保存的自定义EQ模式参数
 * 发送：99 EC B2 00 01 00 12 34
 * 响应：99 EC B2 00 [len] [10个频段gain值] [自定义名称bytes] 12 34
 *   len = 频段(10字节) + 名称字节数
 */
public class CustomEQQueryCommand extends Command {
    private static final String TAG = "CustomEQQueryCmd";
    private static final String COMMAND_PREFIX_STR = "99ECB2";
    private static final String COMMAND_DATA = "99ECB20001001234";

    public CustomEQQueryCommand() {
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
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX_STR)) {
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        try {
            int dataLength = Integer.parseInt(responseData.substring(6, 10), 16);
            if (dataLength < CustomEQRealtimeCommand.BAND_COUNT) {
                return completed(ResultCode.FAILED, null, "响应数据长度不足");
            }
            int valueHexEnd = 10 + dataLength * 2;
            if (responseData.length() < valueHexEnd + 4) {
                return completed(ResultCode.FAILED, null, "响应数据长度不足");
            }
            CustomEqPayload payload = CustomEqPayload.parseValue(
                    responseData.substring(10, valueHexEnd));
            Object[] result = new Object[]{payload.gains, payload.name};
            return completed(ResultCode.SUCCESS, result, "查询自定义EQ成功");
        } catch (Exception e) {
            HsLog.e(TAG, "解析查询响应失败", e);
            return completed(ResultCode.FAILED, null, "解析查询响应失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.length() >= 6
                && COMMAND_PREFIX_STR.equals(responseData.substring(0, 6));
    }

}
