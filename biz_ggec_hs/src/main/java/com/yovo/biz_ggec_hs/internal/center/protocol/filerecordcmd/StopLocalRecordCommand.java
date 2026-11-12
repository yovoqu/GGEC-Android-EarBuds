package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 结束本地录音命令（协议 EC BC）
 * <p>
 * 仅通知设备停止本机 Flash 录音，不启动文件传输。
 * <p>
 * 发送：99 EC BC 00 01 00 12 34
 * <p>
 * 响应：99 EC BC 00 01 xx 12 34
 *   xx：LFS 状态，0x00=就绪/成功，其它=失败
 */
public class StopLocalRecordCommand extends Command {
    private static final String TAG = "StopLocalRecordCommand";

    private static final String COMMAND_PREFIX = "99ECBC";
    private static final String COMMAND_SUFFIX = "1234";

    /** 响应帧最小长度（chars）：99ECBC(6) + 0001(4) + payload(2) + 1234(4) = 16 */
    private static final int MIN_RESPONSE_LENGTH = 16;

    public StopLocalRecordCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
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
                || responseData.length() < MIN_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        if (status == 0x00) {
            String desc = "结束本地录音成功";
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, status, desc);
        }
        String desc = "结束本地录音失败，状态=0x" + String.format("%02X", status);
        HsLog.w(TAG, desc);
        return completed(ResultCode.FAILED, status, desc);
    }
}
