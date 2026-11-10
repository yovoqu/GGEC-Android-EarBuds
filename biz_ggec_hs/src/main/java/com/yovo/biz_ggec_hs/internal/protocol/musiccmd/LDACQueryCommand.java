package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-24
 * Description:
 * LDAC 状态查询命令（协议 EC CF）
 * <p>
 * Query:
 * 99 EC CF 00 01 00 12 34
 * Response:
 * 99 EC CF 00 01 01 12 34 开
 * 99 EC CF 00 01 00 12 34 关
 */
public class LDACQueryCommand extends Command {
    private static final String TAG = "LDACQueryCommand";

    private static final String COMMAND_PREFIX = "99ECCF0001";
    private static final String COMMAND_DATA = COMMAND_PREFIX + "00" + "1234";

    public LDACQueryCommand() {
        super();
        setCommandPrefix("99ECCF");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX, 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        String statusByte = responseData.substring(10, 12);
        Boolean status = ProtocolValues.parseBooleanByte(statusByte);
        if (status == null) {
            return completed(ResultCode.FAILED, 0, "LDAC 状态字节无效");
        }

        int resultValue = status ? 1 : 0;
        String desc = "LDAC 状态: " + (status ? "开启" : "关闭");
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
