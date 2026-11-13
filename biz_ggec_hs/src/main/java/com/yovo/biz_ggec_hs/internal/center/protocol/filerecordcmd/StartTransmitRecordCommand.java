package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 开始同步录音文件命令（协议 EC C4）
 * <p>
 * 发送：99 EC C4 00 01 xx 12 34
 *   xx：文件索引（1 字节）
 * <p>
 * 成功响应：99 EC C4 00 05 00 xx xx xx xx 12 34
 *   bytes[4]：数据长度（含状态字节，成功时为 5 = 状态 1 + 文件大小 4）
 *   bytes[5]：状态 0x00=成功
 *   bytes[6..9]：录音文件大小（4 字节，大端）
 * <p>
 * 失败响应：99 EC C4 00 01 01 12 34
 *   bytes[5]：状态 0x01=失败
 */
public class StartTransmitRecordCommand extends Command {
    private static final String TAG = "StartTransmitRecordCommand";

    private static final String COMMAND_PREFIX = "99ECC4";
    private static final String COMMAND_SUFFIX = "1234";

    /** 失败响应最小长度（chars）：prefix(6) + len(4) + status(2) + suffix(4) = 16 */
    private static final int MIN_FAIL_RESPONSE_LENGTH = 16;

    /** 成功响应最小长度（chars）：prefix(6) + len(4) + status(2) + size(8) + suffix(4) = 24 */
    private static final int MIN_SUCCESS_RESPONSE_LENGTH = 24;

    private final int fileIndex;

    public StartTransmitRecordCommand(int fileIndex) {
        super();
        if (fileIndex < 0 || fileIndex > 0xFF) {
            throw new IllegalArgumentException("录音文件索引必须在 0 到 255 之间");
        }
        this.fileIndex = fileIndex;
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        String command = COMMAND_PREFIX + "0001"
                + String.format("%02X", fileIndex)
                + COMMAND_SUFFIX;
        HsLog.d(TAG, "生成的命令: " + command + "，文件索引=" + fileIndex);
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
                || responseData.length() < MIN_FAIL_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        // 响应帧布局（hex 字符索引）：
        //   [0..5]   99ECC4     CMD
        //   [6..9]   xxxx       数据长度（含状态字节）
        //   [10..11] xx         状态字节（00=成功，01=失败）
        //   [12..19] xxxxxxxx   文件大小（4 字节，仅成功时存在）
        //   [..]     1234       SEQ

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, null, "状态字节解析失败");
        }

        if (status != 0x00) {
            HsLog.w(TAG, "开始同步录音文件失败，状态: 0x" + String.format("%02X", status));
            return completed(ResultCode.FAILED, null, "开始同步录音文件失败");
        }

        if (responseData.length() != MIN_SUCCESS_RESPONSE_LENGTH) {
            HsLog.e(TAG, "成功响应数据长度不足");
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        long fileSize;
        try {
            fileSize = Long.parseLong(responseData.substring(12, 20), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析录音文件大小失败", e);
            return completed(ResultCode.FAILED, null, "录音文件大小解析失败");
        }

        String desc = "开始同步录音文件成功，文件大小=" + fileSize;
        HsLog.i(TAG, desc);
        return completed(ResultCode.SUCCESS, fileSize, desc);
    }
}
