package com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取提示音状态命令
 * 负责发送获取提示音状态的命令，帮助用户获取提示音状态
 * 99 EC 9F 00 01 00 12 34 查询响铃状态
 */
public class FindAlertStatusCommand extends Command {
    private static final String TAG = "FindAlertStatusCommand";
    // 命令前缀
    private static final String COMMAND_PREFIX = "99EC9F0001";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    /**
     * 构造方法
     */
    public FindAlertStatusCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX + "00" + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isFixedFrame(responseData, COMMAND_PREFIX, 16)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 获取命令状态字节
        String statusByteHex = responseData.substring(10, 12);
//        app查询耳机的返回以及耳机主动的上报：
//        99 EC 9F 00 01 00 12 34 : 左耳不响 右耳不响
//        99 EC 9F 00 01 01 12 34 : 左耳响，右耳不响
//        99 EC 9F 00 01 02 12 34 : 左耳不响 右耳响
//        99 EC 9F 00 01 03 12 34 : 左耳响，右耳响
        try {
            // 将十六进制状态转换为整数
            int status = Integer.parseInt(statusByteHex, 16);
            if (status < 0 || status > 3) {
                return completed(ResultCode.FAILED, 0, "提示音状态超出有效范围");
            }
            // 通知命令完成
            return completed(ResultCode.SUCCESS, status, "解析成功");
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析提示音状态失败: " + statusByteHex, e);
            // 通知命令失败
            return completed(ResultCode.FAILED, 0, "解析提示音状态失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应前缀是否匹配
        if (responseData != null && responseData.length() >= 16) {
            return responseData.startsWith(COMMAND_PREFIX);
        }
        return false;
    }
}
