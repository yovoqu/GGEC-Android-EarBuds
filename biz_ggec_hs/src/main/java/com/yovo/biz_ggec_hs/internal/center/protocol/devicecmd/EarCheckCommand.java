package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 语音唤醒命令
 * 负责控制耳机的语音唤醒功能，开启后可通过语音指令激活语音助手
 */
public class EarCheckCommand extends Command {
    private static final String TAG = "EarCheckCommand";

    // 命令前缀，用于确认是语音唤醒命令
    private static final String COMMAND_PREFIX = "99ECA10002";

    // 开启语音唤醒命令
    private static final String COMMAND_ON = COMMAND_PREFIX +"01";

    // 关闭语音唤醒命令
    private static final String COMMAND_OFF = COMMAND_PREFIX +"00";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    // 是否开启
    private final boolean isEnabled;

    /**
     * 构造方法
     * @param isEnabled 是否开启语音唤醒
     */
    public EarCheckCommand(boolean isEnabled) {
        super();
        this.isEnabled = isEnabled;
        // 设置命令前缀
        setCommandPrefix("99ECA1");
    }

    @Override
    public String getCommandData() {
        return (isEnabled ? COMMAND_ON : COMMAND_OFF) + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX, 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取命令状态字节
        String statusByte = responseData.substring(10, 12);

        // 判断是开启还是关闭状态
        Boolean status = ProtocolValues.parseBooleanByte(statusByte);
        int resultValue = Boolean.TRUE.equals(status) ? 1 : 0;

        // 判断设置是否成功
        boolean isSuccess = status != null && status == isEnabled;

        String result = isSuccess ?
                "耳检" + (isEnabled ? "开启" : "关闭") + "成功" :
                "耳检" + (isEnabled ? "开启" : "关闭") + "失败";

        // 通知命令完成（使用带结果值的回调）
        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 10) {
            String prefix = responseData.substring(0, 10);
            return COMMAND_PREFIX.equals(prefix);
        }
        return false;
    }
}
