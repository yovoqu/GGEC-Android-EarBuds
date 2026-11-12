package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * ota状态
 * 负责控制耳机的掉落提醒功能，开启后可在耳机脱落时发出警告音
 */
public class OTAStartCommand extends Command {
    private static final String TAG = "OTAStatusCommand";

    // 命令前缀，用于确认是掉落提醒命令
    private static final String COMMAND_PREFIX = "99ECA4";

    private static final String COMMAND_DATA = "000100";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    /**
     * 构造方法
     */
    public OTAStartCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99ECA4");
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX + COMMAND_DATA + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (responseData == null || responseData.length() < 16) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 判断是开启还是关闭状态
        boolean success = getCommandData().equalsIgnoreCase(responseData);
        return completed(
                success ? ResultCode.SUCCESS : ResultCode.FAILED,
                success ? 1 : 0,
                success ? "OTA 启动命令已确认" : "OTA 启动响应与请求不一致");
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 16) {
            String prefix = responseData.substring(0, 6);
            return COMMAND_PREFIX.equals(prefix);
        }
        return false;
    }
}
