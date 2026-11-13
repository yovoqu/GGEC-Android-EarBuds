package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-09
 * Description:
 * 删除蓝牙缓存已配对设备列表命令（协议 EC A8）
 * <p>
 * 发送：99 EC A8 00 01 00 12 34
 * 响应：99 EC A8 00 01 00 12 34（ACK，原帧回传）
 */
public class DeletePairedDevicesCommand extends Command {
    private static final String TAG = "DeletePairedDevicesCommand";

    private static final String COMMAND_PREFIX = "99ECA8";
    private static final String COMMAND_DATA = "99ECA8000100" + "1234";

    public DeletePairedDevicesCommand() {
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

        if (responseData == null || !COMMAND_DATA.equalsIgnoreCase(responseData)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 协议定义为原帧回传，完整一致才表示设备确认成功。
        return completed(ResultCode.SUCCESS, 1, "删除配对设备列表成功");
    }
}
