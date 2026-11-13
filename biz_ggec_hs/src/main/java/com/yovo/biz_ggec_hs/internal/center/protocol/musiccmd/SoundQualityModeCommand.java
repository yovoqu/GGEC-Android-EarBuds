package com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 设置音质模式命令
 * 负责切换耳机的音质模式，在高音质和高续航模式之间进行切换
 */
public class SoundQualityModeCommand extends Command {
    private static final String TAG = "SoundQualityModeCommand";

    // 命令前缀，用于确认是音质模式命令
    private static final String COMMAND_PREFIX = "99EC810001";

    // 高音质模式命令
    private static final String COMMAND_HIGH_QUALITY = "99EC81000101";

    // 高续航模式命令
    private static final String COMMAND_HIGH_ENDURANCE = "99EC81000100";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    // 是否为高音质模式
    private final boolean isHighQuality;

    /**
     * 构造方法
     * @param isHighQuality 是否为高音质模式，true表示高音质，false表示高续航
     */
    public SoundQualityModeCommand(boolean isHighQuality) {
        super();
        this.isHighQuality = isHighQuality;
    }

    @Override
    public String getCommandData() {
        return (isHighQuality ? COMMAND_HIGH_QUALITY : COMMAND_HIGH_ENDURANCE) + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX, 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取命令状态字节
        String statusByte = responseData.substring(10, 12);

        // 判断是高音质还是高续航状态
        Boolean status = ProtocolValues.parseBooleanByte(statusByte);
        int resultValue = status == null ? 0 : (status ? 0 : 1);

        // 判断设置是否成功
        boolean isSuccess = status != null && status == isHighQuality;

        String result = isSuccess ?
                (isHighQuality ? "高音质模式设置成功" : "高续航模式设置成功") :
                (isHighQuality ? "高音质模式设置失败" : "高续航模式设置失败");

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
