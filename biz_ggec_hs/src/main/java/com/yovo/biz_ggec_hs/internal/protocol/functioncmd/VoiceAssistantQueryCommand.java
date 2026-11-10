package com.yovo.biz_ggec_hs.internal.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-24
 * Description:
 * 语音助手状态查询命令（协议 EC CB）
 * <p>
 * Query:
 * 99 EC CB 00 01 00 12 34
 * Response:
 * 99 EC CB 00 01 00 12 34 睡眠
 * 99 EC CB 00 01 01 12 34 唤醒
 */
public class VoiceAssistantQueryCommand extends Command {
    private static final String TAG = "VoiceAssistantQueryCommand";

    private static final String COMMAND_PREFIX = "99ECCB0001";
    private static final String COMMAND_DATA = COMMAND_PREFIX + "00" + "1234";

    public VoiceAssistantQueryCommand() {
        super();
        setCommandPrefix("99ECCB");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    /**
     * 解析语音助手状态（查询应答与主动上报共用）
     *
     * @return 0=睡眠，1=唤醒；无效帧返回 null
     */
    public static Integer parseStatusCode(String frame) {
        if (!ProtocolValues.isFixedFrame(frame, COMMAND_PREFIX, 16)) {
            return null;
        }
        Boolean status = ProtocolValues.parseBooleanByte(frame.substring(10, 12));
        if (status == null) {
            return null;
        }
        return status ? 1 : 0;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        Integer statusCode = parseStatusCode(responseData);
        if (statusCode == null) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        int resultValue = statusCode;
        String desc = "语音助手状态: " + (resultValue == 1 ? "唤醒" : "睡眠");
        HsLog.i(TAG, desc);
        return completed(ResultCode.SUCCESS, resultValue, desc);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (responseData != null && responseData.length() >= 10) {
            return COMMAND_PREFIX.equals(responseData.substring(0, 10));
        }
        return false;
    }
}
