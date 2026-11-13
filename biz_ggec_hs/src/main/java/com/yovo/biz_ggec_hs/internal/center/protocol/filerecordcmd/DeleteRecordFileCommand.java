package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 批量删除录音文件命令（协议 EC C1）
 * <p>
 * 固件：{@code lfs_delete_file(msg->data, msg->header.data_len_l)}，
 * LEN 即索引个数，payload 仅为索引列表（无前置 count 字节）。
 * <p>
 * 发送：99 EC C1 00 xx [index...] 12 34
 *   xx：数据长度 = 文件索引个数 N（单次最多
 *   {@link SyncRecordFileIdOpCommand#MAX_BATCH_DELETE_COUNT} 个）
 *   index...：文件索引（每个 1 字节）
 * <p>
 * 响应：99 EC C1 00 01 [status] 12 34
 *   status：0x00=成功；非 0 为失败（固件 int8：-1/-2/-3/-4）
 */
public class DeleteRecordFileCommand extends Command {
    private static final String TAG = "DeleteRecordFileCommand";

    private static final String COMMAND_PREFIX = "99ECC1";
    private static final String COMMAND_SUFFIX = "1234";

    /** 批量删除单次最多条目数，与 EC D4 统一。 */
    public static final int MAX_BATCH_DELETE_COUNT =
            SyncRecordFileIdOpCommand.MAX_BATCH_DELETE_COUNT;

    /** 响应帧长度（chars）：99ECC1(6) + 0001(4) + status(2) + 1234(4) = 16 */
    private static final int RESPONSE_LENGTH = 16;

    private final int[] fileIndices;

    /**
     * @param fileIndices 需要删除的文件索引数组，每个索引占 1 字节；数量 1~
     *                    {@link #MAX_BATCH_DELETE_COUNT}
     */
    public DeleteRecordFileCommand(int[] fileIndices) {
        super();
        if (fileIndices == null || fileIndices.length == 0
                || fileIndices.length > MAX_BATCH_DELETE_COUNT) {
            throw new IllegalArgumentException("录音文件索引数量超出协议范围（最多 "
                    + MAX_BATCH_DELETE_COUNT + " 个）");
        }
        for (int index : fileIndices) {
            if (index < 0 || index > 0xFF) {
                throw new IllegalArgumentException("录音文件索引超出协议范围");
            }
        }
        this.fileIndices = fileIndices.clone();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        int count = fileIndices.length;
        // LEN = N；payload = [index...]（与固件 lfs_delete_file 一致）
        StringBuilder sb = new StringBuilder(COMMAND_PREFIX);
        sb.append(String.format("%04X", count));
        for (int index : fileIndices) {
            sb.append(String.format("%02X", index & 0xFF));
        }
        sb.append(COMMAND_SUFFIX);

        String command = sb.toString();
        HsLog.d(TAG, "生成的命令: " + command + "，数量=" + count);
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
                || responseData.length() != RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 1, "响应数据格式错误");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 1, "状态字节解析失败");
        }

        if (status == 0x00) {
            String desc = "删除录音文件成功";
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, 0, desc);
        }
        String desc = "删除录音文件失败，状态: 0x" + String.format("%02X", status);
        HsLog.w(TAG, desc);
        return completed(ResultCode.FAILED, 1, desc);
    }
}
