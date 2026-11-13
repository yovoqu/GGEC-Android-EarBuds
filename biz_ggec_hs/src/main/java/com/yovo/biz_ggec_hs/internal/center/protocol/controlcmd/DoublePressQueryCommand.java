package com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-24
 * Description:
 * 双触查询命令（协议 EC CA）
 * <p>
 * 发送：99 EC CA 00 01 00 12 34
 * <p>
 * 响应：99 EC CA 00 04 [左耳类型] [左耳操作] [右耳类型] [右耳操作] 12 34
 */
public class DoublePressQueryCommand extends Command {
    private static final String TAG = "DoublePressQueryCommand";

    private static final String COMMAND_PREFIX = "99ECCA";
    private static final String COMMAND_DATA = "99ECCA000100" + "1234";
    private static final int RESPONSE_LENGTH = 22;

    public DoublePressQueryCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据格式错误，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        try {
            PressActionConfiguration configuration = PressActionConfiguration.parseDouble(responseData);
            String desc = "双触查询成功: 左耳操作=" + configuration.getLeftAction()
                    + "，右耳操作=" + configuration.getRightAction();
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, configuration, desc);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            HsLog.e(TAG, "解析双触配置失败", e);
            return completed(ResultCode.FAILED, null, "解析双触配置失败");
        }
    }
}
