package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-16
 * Description:
 * 获取双连接开关状态命令（协议 EC AC）
 * <p>
 * 发送：99 EC AC 00 01 00 12 34
 * 响应：99 EC AC 00 01 xx 12 34
 * <p>
 * xx：0x00 = 单连接    0x01 = 双连接
 */
public class GetDualConnectionCommand extends Command {
    private static final String TAG = "GetDualConnectionCommand";

    private static final String COMMAND_PREFIX = "99ECAC";
    private static final String COMMAND_DATA = "99ECAC000100" + "1234";

    /**
     * 响应帧最小长度（chars）：99ECAC(6) + 0001(4) + xx(2) + 1234(4) = 16
     */
    private static final int MIN_RESPONSE_LENGTH = 16;

    public GetDualConnectionCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != MIN_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: " + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]   99ECAC  CMD
        //   [6..9]   0001    LEN
        //   [10..11] xx      状态字节
        //   [12..15] 1234    SEQ
        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        String desc;
        switch (status) {
            case 0x00:
                desc = "单连接";
                break;
            case 0x01:
                desc = "双连接";
                break;
            default:
                desc = "未知状态: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "双连接开关状态: 0x" + String.format("%02X", status) + " " + desc);
        return completed(status == 0x00 || status == 0x01
                ? ResultCode.SUCCESS
                : ResultCode.FAILED, status, desc);
    }
}
