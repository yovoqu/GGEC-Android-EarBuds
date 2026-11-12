package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 设备重启命令
 * 负责控制设备的重启
 */
public class DeviceRebootCommand extends Command {
    private static final String TAG = "DeviceRebootCommand";

    private static final String COMMAND_PREFIX = "99ECA6";

    private static final String COMMAND_DATA = "000100";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    /**
     * 构造方法
     */
    public DeviceRebootCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99ECA6");
    }
    /**
     * 获取命令数据
     * @return 命令数据
     */
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
                success ? "设备重启命令已确认" : "设备重启响应与请求不一致");
    }
    /**
     * 检查响应数据是否匹配
     * @param responseData 响应数据
     * @return 是否匹配
     */
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
