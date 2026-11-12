package com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 右耳寻找提示音命令
 * 负责发送右耳响铃或停止响铃的命令，帮助用户查找右耳机
 */
public class FindAlertRightCommand extends Command {
    private static final String TAG = "FindAlertRightCommand";

    // 命令前缀
    private static final String COMMAND_PREFIX = "99EC8C0002";

    // 右耳
    private static final String EAR_SIDE_RIGHT = "01";

    // 动作
    private static final String ACTION_START = "01";
    private static final String ACTION_STOP = "00";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    private final String command;
    private final boolean start;

    /**
     * 构造方法
     */
    public FindAlertRightCommand() {
        this(true);
    }

    /**
     * 构造方法
     * @param start true为响铃, false为停止
     */
    public FindAlertRightCommand(boolean start) {
        super();
        this.start = start;
        String action = start ? ACTION_START : ACTION_STOP;
        this.command = COMMAND_PREFIX + EAR_SIDE_RIGHT + action + COMMAND_SUFFIX;
        setCommandPrefix(COMMAND_PREFIX + EAR_SIDE_RIGHT);
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

        // 获取状态值
        int resultValue = start ? 1 : 0;

        String result;
        if (start) {
            result = isSuccess ? "右耳机响铃成功" : "右耳机响铃失败";
        } else {
            result = isSuccess ? "右耳机停止响铃成功" : "右耳机停止响铃失败";
        }

        // 通知命令完成（使用带结果值的回调）
        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 12) {
            String prefix = responseData.substring(0, 12);
            return (COMMAND_PREFIX + EAR_SIDE_RIGHT).equalsIgnoreCase(prefix);
        }
        return false;
    }
}