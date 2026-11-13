package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-16
 * Description:
 * 设置双连接开关命令（协议 EC AD）
 * <p>
 * 发送：99 EC AD 00 01 xx 12 34
 * xx：0x00 = 单连接    0x01 = 双连接
 * <p>
 * 响应：99 EC AD 00 01 xx 12 34
 * xx：0x00 = 单连接    0x01 = 双连接    0x02 = 失败
 */
public class SetDualConnectionCommand extends Command {
    private static final String TAG = "SetDualConnectionCommand";

    private static final String COMMAND_PREFIX = "99ECAD";

    /**
     * 响应帧最小长度（chars）：99ECAD(6) + 0001(4) + xx(2) + 1234(4) = 16
     */
    private static final int MIN_RESPONSE_LENGTH = 16;

    private final String commandData;
    private final int expectedMode;

    /**
     * @param isOpen true = 开启双连接（0x01），false = 切换为单连接（0x00）
     */
    public SetDualConnectionCommand(boolean isOpen) {
        super();
        setCommandPrefix(COMMAND_PREFIX);
        expectedMode = isOpen ? 0x01 : 0x00;
        String modeHex = String.format("%02X", expectedMode);
        // HEAD=99EC CMD=AD LEN=0001 DATA=xx SEQ=1234
        this.commandData = "99ECAD0001" + modeHex + "1234";
    }

    @Override
    public String getCommandData() {
        return commandData;
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
        //   [0..5]   99ECAD  CMD
        //   [6..9]   0001    LEN
        //   [10..11] xx      结果状态字节
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
                desc = "已切换为单连接";
                break;
            case 0x01:
                desc = "已切换为双连接";
                break;
            case 0x02:
                desc = "设置失败";
                break;
            default:
                desc = "未知结果: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "设置双连接结果: 0x" + String.format("%02X", status) + " " + desc);
        return completed(status == expectedMode ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }
}
