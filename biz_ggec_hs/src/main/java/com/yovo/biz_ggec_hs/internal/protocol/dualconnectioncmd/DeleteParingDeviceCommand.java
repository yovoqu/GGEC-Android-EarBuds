package com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-10
 * Description:
 * 删除耳机保存的单个已配对设备命令（协议 EC A9，对称收发）
 * <p>
 * 发送：99 EC A9 00 06 [MAC 6字节] 12 34
 * 响应：99 EC A9 00 06 [MAC 6字节] 12 34
 */
public class DeleteParingDeviceCommand extends Command {
    private static final String TAG = "DeleteParingDeviceCommand";

    private static final String COMMAND_PREFIX = "99ECA9";

    private final String commandData;

    /**
     * @param macStr 目标设备蓝牙 MAC 地址，格式 XX:XX:XX:XX:XX:XX 或 XXXXXXXXXXXX
     */
    public DeleteParingDeviceCommand(String macStr) {
        super();
        setCommandPrefix(COMMAND_PREFIX);
        this.commandData = buildCommandData(macStr);
    }

    private static String buildCommandData(String macStr) {
        String macHex = ProtocolValues.normalizeMacAddress(macStr);
        if (macHex == null) {
            throw new IllegalArgumentException("目标设备 MAC 地址格式无效");
        }
        return "99ECA90006" + macHex + "1234";
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
        return completed(ResultCode.SUCCESS, 1, "删除单个配对设备成功");
    }
}
