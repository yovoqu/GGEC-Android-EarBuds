package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.nio.charset.StandardCharsets;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取蓝牙设备名称命令
 * 负责从耳机获取当前设置的蓝牙设备名称
 */
public class DeviceNameGetCommand extends Command {
    private static final String TAG = "DeviceNameGetCommand";

    // 命令前缀，用于确认是获取设备名命令
    private static final String COMMAND_PREFIX = "99EC99";

    // 完整的发送命令
    private static final String COMMAND = "99EC99000100";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    private String parsedDeviceName;

    /**
     * 构造方法
     */
    public DeviceNameGetCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99EC99");
    }

    @Override
    public String getCommandData() {
        return COMMAND + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            String errorMsg = "响应数据格式错误";
            return completed(ResultCode.FAILED, null, errorMsg);
        }

        try {
            // 获取设备名长度（第4和第5位，也就是索引位置6-10）
            String lengthHex = responseData.substring(6, 10);
            int nameLength = Integer.parseInt(lengthHex, 16);

            // 计算设备名结束位置
            int endIndex = 10 + nameLength * 2;

            // 提取设备名称的十六进制字符串
            String deviceNameHex = responseData.substring(10, endIndex);

            // 将十六进制字符串转换为字节数组
            byte[] bytes = new byte[deviceNameHex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(deviceNameHex.substring(i * 2, i * 2 + 2), 16);
            }

            // 使用UTF-8解码字节数组
            String deviceName = new String(bytes, StandardCharsets.UTF_8);
            this.parsedDeviceName = deviceName;

            String message = "获取设备名成功";
            return completed(ResultCode.SUCCESS, deviceName, message);
        } catch (Exception e) {
            HsLog.e(TAG, "解析设备名失败", e);
            String errorMsg = "解析设备名失败: " + e.getMessage();
            return completed(ResultCode.FAILED, null, errorMsg);
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 6) {
            String prefix = responseData.substring(0, 6);
            return COMMAND_PREFIX.equals(prefix);
        }
        return false;
    }

    public String getParsedDeviceName() {
        return parsedDeviceName;
    }
}
