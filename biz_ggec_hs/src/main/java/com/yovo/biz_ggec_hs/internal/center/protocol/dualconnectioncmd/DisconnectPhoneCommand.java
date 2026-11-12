package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-10
 * Description:
 * 断开指定手机命令（协议 EC AA）
 * <p>
 * 发送：99 EC AA 00 06 [MAC 6字节] 12 34
 * 响应：99 EC AA 00 06 [MAC 6字节] 12 34（ACK，原帧回传）
 */
public class DisconnectPhoneCommand extends Command {
    private static final String TAG = "DisconnectPhoneCommand";

    private static final String COMMAND_PREFIX = "99ECAA";

    private final String commandData;

    /**
     * @param macStr 目标手机蓝牙 MAC 地址，格式 XX:XX:XX:XX:XX:XX 或 XXXXXXXXXXXX
     */
    public DisconnectPhoneCommand(String macStr) {
        super();
        setCommandPrefix(COMMAND_PREFIX);
        this.commandData = buildCommandData(macStr);
    }

    private static String buildCommandData(String macStr) {
        String macHex = ProtocolValues.normalizeMacAddress(macStr);
        if (macHex == null) {
            throw new IllegalArgumentException("目标手机 MAC 地址格式无效");
        }
        // HEAD=99EC CMD=AA LEN=0006 DATA=MAC(6bytes) SEQ=1234
        return "99ECAA0006" + macHex + "1234";
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
        if (responseData == null || !commandData.equalsIgnoreCase(responseData)) {
            return completed(ResultCode.FAILED, 0, "设备确认帧与请求不一致");
        }
        return completed(ResultCode.SUCCESS, 1, "断开指定手机成功");
    }
}
