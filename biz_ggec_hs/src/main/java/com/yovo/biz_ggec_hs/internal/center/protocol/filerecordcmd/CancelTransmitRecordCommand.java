package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-05-29
 * Description:
 * 取消传输录音文件命令（协议 EC C5）
 * <p>
 * 发送：99 EC C5 00 01 00 12 34
 * <p>
 * 成功响应：99 EC C5 00 01 00 12 34
 * 失败响应：99 EC C5 00 01 01 12 34
 */
public class CancelTransmitRecordCommand extends Command {
    private static final String TAG = "CancelTransmitRecordCommand";

    private static final String COMMAND_PREFIX = "99ECC5";
    private static final String COMMAND_SUFFIX = "1234";

    /** 响应帧最小长度（chars）：99ECC5(6) + 0001(4) + payload(2) + 1234(4) = 16 */
    private static final int MIN_RESPONSE_LENGTH = 16;

    public CancelTransmitRecordCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        // 固定帧：99ECC5 + 0001(LEN=1) + 00(payload) + 1234
        String command = COMMAND_PREFIX + "000100" + COMMAND_SUFFIX;
        HsLog.d(TAG, "生成的命令: " + command);
        return command;
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
            HsLog.e(TAG, "响应数据长度不足，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（hex 字符索引）：
        //   [0..5]   99ECC5    CMD
        //   [6..9]   0001      LEN
        //   [10..11] xx        payload（00=成功，01=失败）
        //   [12..15] 1234      SEQ

        int payload;
        try {
            payload = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析响应载荷失败", e);
            return completed(ResultCode.FAILED, 0, "解析响应载荷失败");
        }

        if (payload == 0x00) {
            String desc = "取消传输录音文件成功";
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, payload, desc);
        } else {
            String desc = "取消传输录音文件失败，状态=0x" + String.format("%02X", payload);
            HsLog.w(TAG, desc);
            return completed(ResultCode.FAILED, payload, desc);
        }
    }
}
