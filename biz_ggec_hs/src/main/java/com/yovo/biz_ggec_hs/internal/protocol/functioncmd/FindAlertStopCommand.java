package com.yovo.biz_ggec_hs.internal.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 左右耳统一关闭提示音命令
 * 负责发送左右耳关闭提示音的命令，帮助用户关闭左右耳机的提示音
 */
public class FindAlertStopCommand extends Command {
    private static final String TAG = "FindAlertStopCommand";

    // 命令前缀
    private static final String COMMAND_PREFIX = "99EC8C0002";

    // 左耳
    private static final String EAR_SIDE = "02";

    // 动作
    private static final String ACTION_STOP = "00";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    private final String command;

    public FindAlertStopCommand() {
        super();
        this.command = COMMAND_PREFIX + EAR_SIDE + ACTION_STOP + COMMAND_SUFFIX;
        setCommandPrefix(COMMAND_PREFIX + EAR_SIDE);
    }

    @Override
    public String getCommandData() {
        return command;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (responseData == null || responseData.length() < 18) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 检查响应是否与命令匹配
        boolean isSuccess = getCommandData().equalsIgnoreCase(responseData);

        String result = isSuccess ? "双耳耳机停止响铃成功" : "双耳耳机停止响铃失败";

        // 通知命令完成（使用带结果值的回调）
        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, 0, result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 12) {
            String prefix = responseData.substring(0, 12);
            return (COMMAND_PREFIX + EAR_SIDE).equalsIgnoreCase(prefix);
        }
        return false;
    }
}
