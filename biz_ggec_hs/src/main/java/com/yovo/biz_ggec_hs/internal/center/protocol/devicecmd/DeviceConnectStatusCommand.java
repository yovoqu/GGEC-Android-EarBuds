package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取设备连接状态
 * 负责发送获取设备连接状态的命令，帮助用户获取设备连接状态
 * 命令格式：
 * 99 EC A0 00 02 00 00 12 34
 * 响应格式：
 * 99 EC A0 00 02 xx xx 12 34 :
 * 00 bt连接设备数为0     00 ble连接设备数为0
 * 01 bt连接设备数为1     01 ble连接设备数为1
 * 02 bt连接设备数为2     02 ble连接设备数为2
 */
public class DeviceConnectStatusCommand extends Command {
    private static final String TAG = "DeviceConnectStatusCommand";
    // 命令前缀
    private static final String COMMAND_PREFIX = "99ECA00002";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    private int btStatus;
    private int bleStatus;

    /**
     * 构造方法
     */
    public DeviceConnectStatusCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    public int getBtStatus() {
        return btStatus;
    }

    public int getBleStatus() {
        return bleStatus;
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX + "0000" + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, "99ECA0")
                || responseData.length() != 18) {
            return completed(ResultCode.FAILED, new HashMap<>(), "响应数据格式错误");
        }

        // 获取命令状态字节
        String btStatusByteHex = responseData.substring(10, 12);
        String bleStatusByteHex = responseData.substring(12, 14);
 //* 响应格式：
 //* 99 EC A0 00 02 xx xx 12 34 :
 //* 00 bt连接设备数为0     00 ble连接设备数为0
 //* 01 bt连接设备数为1     01 ble连接设备数为1
 //* 02 bt连接设备数为2     02 ble连接设备数为2
        try {
            // 将十六进制状态转换为整数
            int btStatus = Integer.parseInt(btStatusByteHex, 16);
            int bleStatus = Integer.parseInt(bleStatusByteHex, 16);
            if (btStatus > 2 || bleStatus > 2) {
                return completed(ResultCode.FAILED, new HashMap<>(), "连接数量超出协议范围");
            }

            Map<String, Integer> statusMap = new LinkedHashMap<>();
            statusMap.put("btStatus", btStatus);
            statusMap.put("bleStatus", bleStatus);
            this.btStatus = btStatus;
            this.bleStatus = bleStatus;

            // 通知命令完成
            return completed(ResultCode.SUCCESS, statusMap, "解析成功");
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态失败: " + btStatusByteHex + " " + bleStatusByteHex, e);
            // 通知命令失败
            return completed(ResultCode.FAILED, new HashMap<>(), "解析状态失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 18) {
            return responseData.startsWith(COMMAND_PREFIX);
        }
        return false;
    }
}
