package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-05-29
 * Description:
 * 清空录音文件命令（协议 EC C2）
 * <p>
 * 发送：99 EC C2 00 01 00 12 34
 * <p>
 * 成功响应：99 EC C2 00 01 00 12 34
 * 失败响应：99 EC C2 00 01 01 12 34
 */
public class CleanRecordCommand extends Command {
    private static final String TAG = "CleanRecordCommand";

    private static final String COMMAND_PREFIX = "99ECC2";
    private static final String COMMAND_SUFFIX = "1234";

    /** 响应帧最小长度（chars）：99ECC2(6) + 0001(4) + payload(2) + 1234(4) = 16 */
    private static final int MIN_RESPONSE_LENGTH = 16;

    public CleanRecordCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        // 固定帧：99ECC2 + 0001(LEN=1) + 00(payload) + 1234
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
        //   [0..5]   99ECC2    CMD
        //   [6..9]   0001      LEN
        //   [10..11] xx        payload（设备端返回的被清空文件数量；00 通常表示成功）
        //   [12..15] 1234      SEQ

        int payload;
        try {
            payload = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析响应载荷失败", e);
            return completed(ResultCode.FAILED, 0, "解析响应payload失败");
        }

        if (payload == 0x00) {
            String desc = "清空录音文件成功，数量=" + payload;
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, payload, desc);
        } else {
            String desc = "清空录音文件失败，状态=0x" + String.format("%02X", payload);
            HsLog.w(TAG, desc);
            return completed(ResultCode.FAILED, payload, desc);
        }
    }
}
