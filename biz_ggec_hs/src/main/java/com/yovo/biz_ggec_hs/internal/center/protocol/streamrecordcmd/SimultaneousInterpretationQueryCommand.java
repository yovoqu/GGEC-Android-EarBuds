package com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 同声传译状态查询命令（协议 EC DB）。
 * <p>
 * 查询：99 EC DB 00 01 02 12 34
 * 响应：99 EC DB 00 01 00 12 34 空闲
 * 响应：99 EC DB 00 01 01 12 34 正在同声传译
 */
public class SimultaneousInterpretationQueryCommand extends Command {
    private static final String TAG = "SimultaneousInterpretationQueryCommand";

    private static final String COMMAND_PREFIX = "99ECDB0001";
    private static final String COMMAND_DATA = COMMAND_PREFIX + "02" + "1234";

    public SimultaneousInterpretationQueryCommand() {
        super();
        setCommandPrefix("99ECDB");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    /**
     * 解析同声传译状态。
     *
     * @return 0=空闲，1=正在同声传译；无效帧返回 null
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
        String desc = "同声传译状态: " + (resultValue == 1 ? "正在同声传译" : "空闲");
        HsLog.i(TAG, desc);
        return completed(ResultCode.SUCCESS, resultValue, desc);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (responseData != null && responseData.length() >= 10) {
            return COMMAND_PREFIX.equalsIgnoreCase(responseData.substring(0, 10));
        }
        return false;
    }
}
