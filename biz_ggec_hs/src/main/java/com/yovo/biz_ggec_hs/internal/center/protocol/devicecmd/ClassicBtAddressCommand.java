package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取经典蓝牙MAC地址命令
 * 负责从耳机获取经典蓝牙（SPP）的MAC地址信息
 */
public class ClassicBtAddressCommand extends Command {
    private static final String TAG = "ClassicBtAddressCommand";

    // 命令前缀，用于确认是获取经典蓝牙MAC地址命令
    private static final String COMMAND_PREFIX = "99EC97";

    // 完整的发送命令
    private static final String COMMAND = "99EC97000100";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    private String parsedMacAddress;

    /**
     * 构造方法
     */
    public ClassicBtAddressCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99EC97");
    }

    @Override
    public String getCommandData() {
        return COMMAND + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != 26) {
            String errorMsg = "响应数据格式错误";
            return completed(ResultCode.FAILED, null, errorMsg);
        }

        try {
            // 提取MAC地址部分（6个字节，12个16进制字符）
            String macHex = responseData.substring(10, 22);

            // 将MAC地址反向并转换为标准格式 XX:XX:XX:XX:XX:XX
            // 反向解析: 将字节顺序反转 (每2个十六进制字符表示一个字节)
            StringBuilder macAddress = new StringBuilder();
            for (int i = macHex.length() - 2; i >= 0; i -= 2) {
                if (macAddress.length() > 0) {
                    macAddress.append(":");
                }
                macAddress.append(macHex.substring(i, i + 2).toUpperCase());
            }

            String macAddressStr = macAddress.toString();
            this.parsedMacAddress = macAddressStr;
            String message = "获取经典蓝牙MAC地址成功";
            return completed(ResultCode.SUCCESS, macAddressStr, message);
        } catch (Exception e) {
            HsLog.e(TAG, "解析MAC地址失败", e);
            String errorMsg = "解析MAC地址失败";
            return completed(ResultCode.FAILED, null, errorMsg);
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    public String getParsedMacAddress() {
        return parsedMacAddress;
    }
}
