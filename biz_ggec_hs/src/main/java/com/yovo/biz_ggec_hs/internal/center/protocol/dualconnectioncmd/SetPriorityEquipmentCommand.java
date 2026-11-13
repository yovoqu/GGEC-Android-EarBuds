package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-16
 * Description:
 * 设置优先设备命令（协议 EC AF）
 * <p>
 * 发送：99 EC AF 00 06 xx xx xx xx xx xx 12 34
 * xx xx xx xx xx xx：优先设备的蓝牙 MAC 地址（6 字节）
 * <p>
 * 响应：99 EC AF 00 07 xx xx xx xx xx xx xx 12 34
 * 第 1 个 xx：状态字节
 * 0x00 = 设置成功
 * 0x01 = 无法设置优先设备
 * 后 6 个 xx：优先设备的蓝牙 MAC 地址
 */
public class SetPriorityEquipmentCommand extends Command {
    private static final String TAG = "SetPriorityEquipmentCommand";

    private static final String COMMAND_PREFIX = "99ECAF";

    /**
     * 响应帧最小长度（chars）：99ECAF(6) + 0007(4) + status(2) + MAC(12) + 1234(4) = 28
     */
    private static final int MIN_RESPONSE_LENGTH = 28;

    private final String commandData;

    /**
     * @param macStr 目标优先设备蓝牙 MAC 地址，格式 XX:XX:XX:XX:XX:XX 或 XXXXXXXXXXXX
     */
    public SetPriorityEquipmentCommand(String macStr) {
        super();
        setCommandPrefix(COMMAND_PREFIX);
        String macHex = ProtocolValues.normalizeMacAddress(macStr);
        if (macHex == null) {
            throw new IllegalArgumentException("优先设备 MAC 地址格式无效");
        }
        // HEAD=99EC CMD=AF LEN=0006 DATA=MAC(6bytes) SEQ=1234
        this.commandData = "99ECAF0006" + macHex + "1234";
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
                || responseData.length() != MIN_RESPONSE_LENGTH
                || !responseData.regionMatches(true, 12, commandData, 10, 12)) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: " + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]   99ECAF  CMD
        //   [6..9]   0007    LEN
        //   [10..11] xx      状态字节
        //   [12..23] MAC     6 字节优先设备地址
        //   [24..27] 1234    SEQ
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
                desc = "设置优先设备成功";
                break;
            case 0x01:
                desc = "无法设置优先设备";
                break;
            default:
                desc = "未知结果: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "设置优先设备结果: 0x" + String.format("%02X", status) + " " + desc);
        return completed(status == 0x00 ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }
}
