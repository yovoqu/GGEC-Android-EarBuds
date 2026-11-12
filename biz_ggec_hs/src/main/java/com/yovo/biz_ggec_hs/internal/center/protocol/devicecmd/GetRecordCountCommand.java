package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-05-22
 * Description:
 * 获取录音文件总数量命令（协议 EC BF）
 * <p>
 * 发送：99 EC BF 00 01 00 12 34
 * <p>
 * 成功响应：99 EC BF 00 02 00 xx 12 34
 * 00：状态字节（0x00=成功）
 * xx：录音文件总数量
 * <p>
 * 失败响应：99 EC BF 00 02 01 00 12 34
 * 01：状态字节（0x01=失败）
 */
public class GetRecordCountCommand extends Command {
    private static final String TAG = "GetRecordCountCommand";

    private static final String COMMAND_PREFIX = "99ECBF";
    private static final String COMMAND_DATA = "99ECBF000100" + "1234";

    /**
     * 响应帧最小长度（chars）：
     * 99ECBF(6) + 0002(4) + status(2) + count(2) + 1234(4) = 18
     */
    private static final int MIN_RESPONSE_LENGTH = 18;

    public GetRecordCountCommand() {
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
                || responseData.length() != MIN_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: " + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]   99ECBF    CMD
        //   [6..9]   0002      LEN
        //   [10..11] xx        状态字节（00=成功，01=失败）
        //   [12..13] xx        录音文件总数量
        //   [14..17] 1234      SEQ

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        if (status != 0x00) {
            HsLog.w(TAG, "获取录音文件数量失败，状态: 0x" + String.format("%02X", status));
            return completed(ResultCode.FAILED, 0, "获取录音文件数量失败");
        }

        int recordCount;
        try {
            recordCount = Integer.parseInt(responseData.substring(12, 14), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析录音文件数量失败", e);
            return completed(ResultCode.FAILED, 0, "录音文件数量解析失败");
        }

        String desc = "获取录音文件数量成功: " + recordCount;
        HsLog.i(TAG, desc);
        return completed(ResultCode.SUCCESS, recordCount, desc);
    }
}
