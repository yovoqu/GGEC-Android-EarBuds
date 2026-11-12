package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.nio.charset.StandardCharsets;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取充电盒SN
 * 负责从充电盒获取当前SN号信息
 */
public class CaseSNCommand extends Command {
    private static final String TAG = "CaseSNCommand";

    // 命令前缀，用于确认是获取SN命令
    private static final String COMMAND_PREFIX = "99ECA5";

    // 命令结束标识
    private static final String COMMAND_SUFFIX = "1234";

    // 发送命令数据
    private static final String COMMAND_DATA = COMMAND_PREFIX + "000100" + COMMAND_SUFFIX;

    // SN结果
    private String sn = "";

    /**
     * 构造方法
     */
    public CaseSNCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99ECA5");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        try {
            // 检查前缀是否匹配(99ECA5)
            if (!responseData.startsWith(COMMAND_PREFIX)) {
                return completed(ResultCode.FAILED, null, "响应数据前缀不匹配");
            }

            // 提取长度信息（第4、5位，对应原始响应中的xx xx）
            int startIndex = COMMAND_PREFIX.length();
            if (responseData.length() < startIndex + 4) {
                return completed(ResultCode.FAILED, null, "响应数据长度不足");
            }

            // 提取SN数据（从第6位开始，到结束标识前）
            int snStartIndex = startIndex + 4;
            int snEndIndex = responseData.length() - COMMAND_SUFFIX.length();

            if (snEndIndex <= snStartIndex) {
                return completed(ResultCode.FAILED, null, "响应数据格式错误，未找到结束标识");
            }

            String snHexData = responseData.substring(snStartIndex, snEndIndex);
            if (snHexData.length() <= 6 || snHexData.startsWith("000000") || snHexData.startsWith("FFFFFF")) {
                sn = "";
                // 通知命令完成，yobackdata为版本号字符串
                return completed(ResultCode.SUCCESS, sn, "获取SN成功");
            }
            // 转换十六进制字符串为字节数组
            byte[] bytes = new byte[snHexData.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                int index = i * 2;
                bytes[i] = (byte) Integer.parseInt(snHexData.substring(index, index + 2), 16);
            }

            // 将字节数组用UTF-8解码
            sn = new String(bytes, StandardCharsets.UTF_8);

            // 通知命令完成，yobackdata为版本号字符串
            return completed(ResultCode.SUCCESS, sn, "获取SN成功");
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            HsLog.e(TAG, "解析版本号失败", e);

            // 通知命令失败，yobackdata为null
            return completed(ResultCode.FAILED, null, "版本号数据解析错误: " + e.getMessage());
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        return responseData != null && responseData.length() >= 6 && responseData.startsWith(COMMAND_PREFIX);
    }

    /**
     * 获取SN
     * @return SN字符串
     */
    public String getSN() {
        return sn;
    }
}
