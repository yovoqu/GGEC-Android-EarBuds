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
 * app上报与获取当前音乐传输格式
 * （0 : SBC  2: AAC  3:LDAC）
 *
 * 命令格式：
 * 99 EC A2 00 01 05 12 34
 *
 * 响应格式：
 * 99 EC A2 00 01 00 12 34    SBC
 * 99 EC A2 00 01 02 12 34    AAC
 * 99 EC A2 00 01 03 12 34    LDAC
 * 99 EC A2 00 01 ff 12 34    not support
 * 设备主动上报
 */
public class A2dpCodeTypeCommand extends Command {
    private static final String TAG = "A2dpCodeTypeCommand";
    // 命令前缀
    private static final String COMMAND_PREFIX = "99ECA20001";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    /**
     * 构造方法
     */
    public A2dpCodeTypeCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX + "05" + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);
        if (!ProtocolValues.isCompleteFrame(responseData, "99ECA2")
                || responseData.length() != 16) {
            return completed(ResultCode.FAILED, -1, "响应数据格式错误");
        }

        // 获取命令状态字节
        String codeTypeByteHex = responseData.substring(10, 12);
        if("ff".equalsIgnoreCase(codeTypeByteHex)){
            return completed(ResultCode.SUCCESS, -1, "解析成功");
        }
 //* 响应格式：
 //* 99 EC A2 00 01 00 12 34    SBC
 //* 99 EC A2 00 01 02 12 34    AAC
 //* 99 EC A2 00 01 03 12 34    LDAC
 //* 99 EC A2 00 01 ff 12 34    not support
        try {
            // 将十六进制状态转换为整数
            int codeType = Integer.parseInt(codeTypeByteHex, 16);
            if (codeType != 0 && codeType != 2 && codeType != 3) {
                return completed(ResultCode.FAILED, -1, "A2DP 编码类型超出协议范围");
            }
            if (codeType == 3){
                // 奇怪的逻辑 sdk内部转成1给到app
                codeType = 1;
            }
            // 通知命令完成
            return completed(ResultCode.SUCCESS, codeType, "解析成功");
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态失败: " + codeTypeByteHex, e);
            // 通知命令失败
            return completed(ResultCode.FAILED, -1, "解析状态失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 16) {
            return responseData.startsWith(COMMAND_PREFIX);
        }
        return false;
    }
}
