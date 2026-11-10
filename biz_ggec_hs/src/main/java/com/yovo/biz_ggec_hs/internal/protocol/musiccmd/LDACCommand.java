package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-23
 * Description:
 * LDAC 开关设置命令（协议 EC CE）
 * <p>
 * Set:
 * 99 EC CE 00 01 01 12 34 开
 * 99 EC CE 00 01 00 12 34 关
 * Response（回显）:
 * 99 EC CE 00 01 01 12 34 开
 * 99 EC CE 00 01 00 12 34 关
 */
public class LDACCommand extends Command {
    private static final String TAG = "LDACCommand";

    private static final String COMMAND_PREFIX = "99ECCE0001";
    private static final String COMMAND_ON = "99ECCE000101";
    private static final String COMMAND_OFF = "99ECCE000100";
    private static final String COMMAND_SUFFIX = "1234";

    private final boolean isEnabled;

    /**
     * @param isEnabled 是否开启 LDAC
     */
    public LDACCommand(boolean isEnabled) {
        super();
        this.isEnabled = isEnabled;
        setCommandPrefix("99ECCE");
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

        String statusByte = responseData.substring(10, 12);
        Boolean status = ProtocolValues.parseBooleanByte(statusByte);
        int resultValue = Boolean.TRUE.equals(status) ? 1 : 0;
        boolean isSuccess = status != null && status == isEnabled;

        String result = isSuccess
                ? "LDAC" + (isEnabled ? "开启" : "关闭") + "成功"
                : "LDAC" + (isEnabled ? "开启" : "关闭") + "失败";

        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (responseData != null && responseData.length() >= 10) {
            return COMMAND_PREFIX.equals(responseData.substring(0, 10));
        }
        return false;
    }
}
