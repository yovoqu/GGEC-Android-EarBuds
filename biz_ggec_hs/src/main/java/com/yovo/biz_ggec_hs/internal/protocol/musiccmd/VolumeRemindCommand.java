package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 音量提醒命令
 * 负责控制耳机的音量提醒功能，在音量达到可能损伤听力的水平时发出警告
 */
public class VolumeRemindCommand extends Command {
    private static final String TAG = "VolumeRemindCommand";

    // 命令前缀，用于确认是音量提醒命令
    private static final String COMMAND_PREFIX = "99EC840002";

    // 开启音量提醒命令
    private static final String COMMAND_ON = "99EC8400020001";

    // 关闭音量提醒命令
    private static final String COMMAND_OFF = "99EC8400020000";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    // 是否开启
    private final boolean isEnabled;

    /**
     * 构造方法
     * @param isEnabled 是否开启音量提醒
     */
    public VolumeRemindCommand(boolean isEnabled) {
        super();
        this.isEnabled = isEnabled;
    }

    @Override
    public String getCommandData() {
        return (isEnabled ? COMMAND_ON : COMMAND_OFF) + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX + "00", 18)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取命令状态字节
        String statusByte = responseData.substring(12, 14);

        // 判断是开启还是关闭状态
        Boolean status = ProtocolValues.parseBooleanByte(statusByte);
        int resultValue = Boolean.TRUE.equals(status) ? 1 : 0;

        // 判断设置是否成功
        boolean isSuccess = status != null && status == isEnabled;

        String result = isSuccess ?
                "音量提醒" + (isEnabled ? "开启" : "关闭") + "成功" :
                "音量提醒" + (isEnabled ? "开启" : "关闭") + "失败";

        // 通知命令完成（使用带结果值的回调）
        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 12) {
            String prefix = responseData.substring(0, 10);
            String functionCode = responseData.substring(10, 12);
            return COMMAND_PREFIX.equals(prefix) && "00".equals(functionCode);
        }
        return false;
    }
}
