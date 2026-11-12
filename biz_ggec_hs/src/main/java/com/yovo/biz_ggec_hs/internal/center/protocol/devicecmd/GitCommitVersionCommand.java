package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.nio.charset.StandardCharsets;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取git commit信息
 * 99 EC A3 00 01 00 12 34
 * 99 EC A3 00 0x xx xx xx 12 34
 */
public class GitCommitVersionCommand extends Command {
    private static final String TAG = "GitCommitVersionCommand";

    // 命令前缀，用于确认是获取固件版本命令
    private static final String COMMAND_PREFIX = "99ECA3";

    // 发送命令数据
    private static final String COMMAND_DATA = "99ECA3000100" + "1234";

    // 命令结束标识
    private static final String COMMAND_SUFFIX = "1234";

    // 版本号结果
    private String firmwareVersion = "";

    /**
     * 构造方法
     */
    public GitCommitVersionCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99ECA3");
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
            // 检查前缀是否匹配(99ECA3)
            if (!responseData.startsWith(COMMAND_PREFIX)) {
                return completed(ResultCode.FAILED, null, "响应数据前缀不匹配");
            }

            // 提取长度信息（第4、5位，对应原始响应中的xx xx）
            int startIndex = COMMAND_PREFIX.length();
            if (responseData.length() < startIndex + 4) {
                return completed(ResultCode.FAILED, null, "响应数据长度不足");
            }

            // 提取版本号数据（从第6位开始，到结束标识前）
            int versionStartIndex = startIndex + 4;
            int versionEndIndex = responseData.length() - COMMAND_SUFFIX.length();

            if (versionEndIndex <= versionStartIndex) {
                return completed(ResultCode.FAILED, null, "响应数据格式错误，未找到结束标识");
            }

            String versionHexData = responseData.substring(versionStartIndex, versionEndIndex);

            // 转换十六进制字符串为字节数组
            byte[] bytes = new byte[versionHexData.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                int index = i * 2;
                bytes[i] = (byte) Integer.parseInt(versionHexData.substring(index, index + 2), 16);
            }

            // 将字节数组用UTF-8解码
            firmwareVersion = new String(bytes, StandardCharsets.UTF_8);

            // 通知命令完成，yobackdata为版本号字符串
            return completed(ResultCode.SUCCESS, firmwareVersion, "获取版本成功");
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
     * 获取固件版本号
     * @return 固件版本号字符串
     */
    public String getGitCommitVersion() {
        return firmwareVersion;
    }
}
