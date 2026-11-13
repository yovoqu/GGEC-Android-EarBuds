package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 清除配对记录命令
 * 负责清除耳机上存储的蓝牙配对历史记录
 */
public class ClearPairingCommand extends Command {
    private static final String TAG = "ClearPairingCommand";

    // 命令前缀，用于确认是清除配对记录命令
    private static final String COMMAND_PREFIX = "99EC930001";

    // 发送命令数据
    private static final String COMMAND_DATA = "99EC93000100" + "1234";

    /**
     * 构造方法
     */
    public ClearPairingCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99EC93");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX, 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取状态字节
        String statusByte = responseData.substring(10, 12);

        // 判断是否成功
        boolean isSuccess = "00".equals(statusByte);
        int resultValue = isSuccess ? 1 : 0;
        String result = isSuccess ? "清除配对记录成功" : "清除配对记录失败";

        // 通知命令完成
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
