package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.nio.charset.StandardCharsets;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 修改设备经典蓝牙名称命令
 * 负责设置耳机的自定义蓝牙名称，最大支持210字节
 * 设置了3.5秒的超时时间以适应设备重命名操作的实际响应时间
 */
public class DeviceRenameCommand extends Command {
    private static final String TAG = "DeviceRenameCommand";

    // 命令前缀，用于确认是修改耳机名称命令
    private static final String COMMAND_PREFIX = "99EC92";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    // 最大支持的设备名称字节长度
    private static final int MAX_NAME_BYTES = 210;

    // 设备重命名命令的超时时间（毫秒）
    private static final long RENAME_COMMAND_TIMEOUT = 3500;

    // 设置的设备名称
    private final String deviceName;

    /**
     * 构造方法
     * @param deviceName 要设置的设备名称
     */
    public DeviceRenameCommand(String deviceName) {
        super();
        if (deviceName == null || deviceName.isEmpty()) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        if (deviceName.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            throw new IllegalArgumentException("设备名称 UTF-8 编码不能超过 210 字节");
        }
        this.deviceName = deviceName;
        // 设置命令前缀
        setCommandPrefix("99EC92");
    }

    @Override
    public String getCommandData() {
        byte[] nameBytes = deviceName.getBytes(StandardCharsets.UTF_8);

        String lengthHex = String.format("%04X", nameBytes.length);
        HsLog.d(TAG, "设备名称 UTF-8 编码字节长度=" + nameBytes.length);
        StringBuilder hexBuilder = new StringBuilder(nameBytes.length * 2);
        for (byte b : nameBytes) {
            hexBuilder.append(String.format("%02X", b & 0xff));
        }
        return COMMAND_PREFIX + lengthHex + hexBuilder + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX + "0001", 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取命令状态字节 (第7个字节，响应格式中的第6个字节)
        // 在十六进制字符串中，位置为10-12
        String statusByte = responseData.substring(10, 12);

        int resultCode = ResultCode.FAILED;
        int resultValue = 0;
        String result;

        if ("00".equalsIgnoreCase(statusByte)) {
            resultCode = ResultCode.SUCCESS;
            result = "修改耳机名称成功: " + deviceName;
            resultValue = 1;
        } else if ("01".equalsIgnoreCase(statusByte)) {
            result = "修改耳机名称失败: 名称长度超过限制";
            resultValue = 304;
        } else if ("02".equalsIgnoreCase(statusByte)) {
            result = "修改耳机名称失败: TWS未配对";
            resultValue = 305;
        } else {
            result = "修改耳机名称失败: 未知错误，状态码: " + statusByte;
            resultValue = 404;
        }

        // 通知命令完成
        return completed(resultCode, resultValue, result);
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

    @Override
    public long getTimeoutMs() {
        return RENAME_COMMAND_TIMEOUT;
    }

}
