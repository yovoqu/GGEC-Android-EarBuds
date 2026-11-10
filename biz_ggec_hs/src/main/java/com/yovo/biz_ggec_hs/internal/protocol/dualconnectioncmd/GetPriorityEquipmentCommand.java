package com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-16
 * Description:
 * 优先设备查询命令（协议 EC AE）
 * <p>
 * 发送：99 EC AE 00 01 00 12 34
 * 响应：99 EC AE 00 07 xx xx xx xx xx xx xx 12 34
 * <p>
 * 响应字段说明：
 * 第 1 个 xx：状态字节
 * 0x00 = 查询成功
 * 0x01 = 无设置优先设备
 * 后 6 个 xx：优先设备的蓝牙 MAC 地址（仅 status=0x00 时有效）
 * <p>
 * 解析结果通过 {@code Map<String, String>} 传出，键名：
 * {@code "status"}  - 状态字节十进制字符串
 * {@code "address"} - MAC 地址（格式 XX:XX:XX:XX:XX:XX）；status≠0x00 时为空字符串
 */
public class GetPriorityEquipmentCommand extends Command {
    private static final String TAG = "GetPriorityEquipmentCommand";

    private static final String COMMAND_PREFIX = "99ECAE";
    private static final String COMMAND_DATA = "99ECAE000100" + "1234";

    /**
     * 响应帧最小长度（chars）：99ECAE(6) + 0007(4) + status(2) + MAC(12) + 1234(4) = 28
     */
    private static final int MIN_RESPONSE_LENGTH = 28;

    /** Map 键：状态字节（十进制字符串） */
    public static final String KEY_STATUS = "status";
    /** Map 键：MAC 地址（格式 XX:XX:XX:XX:XX:XX） */
    public static final String KEY_ADDRESS = "address";

    public GetPriorityEquipmentCommand() {
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
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]   99ECAE  CMD
        //   [6..9]   0007    LEN
        //   [10..11] xx      状态字节
        //   [12..23] MAC     6 字节优先设备地址
        //   [24..27] 1234    SEQ
        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, null, "状态字节解析失败");
        }

        String macAddress = "";
        if (status == 0x00) {
            macAddress = hexToMacAddress(responseData.substring(12, 24));
        }

        Map<String, String> result = new HashMap<>();
        result.put(KEY_STATUS, String.valueOf(status));
        result.put(KEY_ADDRESS, macAddress);

        String desc;
        switch (status) {
            case 0x00:
                desc = "查询成功，优先设备: " + macAddress;
                break;
            case 0x01:
                desc = "无设置优先设备";
                break;
            default:
                desc = "未知状态: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "优先设备查询结果: " + desc);
        return completed(status == 0x00 || status == 0x01
                ? ResultCode.SUCCESS
                : ResultCode.FAILED, result, desc);
    }

    /**
     * 将 12 位十六进制字符串转换为 MAC 地址格式（XX:XX:XX:XX:XX:XX）
     */
    private String hexToMacAddress(String hex) {
        if (hex == null || hex.length() != 12) return hex != null ? hex : "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (sb.length() > 0) sb.append(':');
            sb.append(hex, i, i + 2);
        }
        return sb.toString().toUpperCase();
    }
}
