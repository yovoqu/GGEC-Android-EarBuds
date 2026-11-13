package com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 按录音 ID 同步/删除命令（协议 EC D4）。
 * <p>
 * 发送：{@code 99 EC D4 00 LL [flags] [id...] 12 34}
 * <ul>
 *   <li>{@code flags}：删除左耳 {@code 0x22} / 删除右耳 {@code 0x12}（可批量）；
 *       导出左耳 {@code 0x21} / 导出右耳 {@code 0x11}（导出仅单 id）</li>
 *   <li>{@code id...}：每个录音 ID 占 4 字节小端无符号值</li>
 *   <li>{@code LL} = 1 + 4×N</li>
 * </ul>
 * 响应：{@code 99 EC D4 00 01 [status] 12 34}，status {@code 0x00}=成功，非 0 失败。
 */
public class SyncRecordFileIdOpCommand extends Command {
    private static final String TAG = "SyncRecordFileIdOpCommand";

    private static final String COMMAND_PREFIX = "99ECD4";
    private static final String COMMAND_SUFFIX = "1234";

    private static final int FLAG_EXPORT = 0x01;
    private static final int FLAG_DELETE = 0x02;
    private static final int FLAG_EAR_RIGHT = 0x10;
    private static final int FLAG_EAR_LEFT = 0x20;

    /** 删除 + 左耳 */
    public static final int FLAGS_DELETE_LEFT = FLAG_EAR_LEFT | FLAG_DELETE;
    /** 删除 + 右耳 */
    public static final int FLAGS_DELETE_RIGHT = FLAG_EAR_RIGHT | FLAG_DELETE;
    /** 导出 + 左耳 */
    public static final int FLAGS_EXPORT_LEFT = FLAG_EAR_LEFT | FLAG_EXPORT;
    /** 导出 + 右耳 */
    public static final int FLAGS_EXPORT_RIGHT = FLAG_EAR_RIGHT | FLAG_EXPORT;

    /**
     * 批量删除单次最多条目数（与 EC C1 Index 删除统一为 8）。
     * payload：1(flags)+4×8=33，落在固件 MAIL 缓冲内。
     */
    public static final int MAX_BATCH_DELETE_COUNT = 8;

    private static final int RESPONSE_LENGTH = 16;
    private static final long MAX_UINT32 = 0xFFFFFFFFL;

    private final int flags;
    private final long[] recordIds;

    /**
     * @param flags     {@link #FLAGS_DELETE_LEFT} / {@link #FLAGS_DELETE_RIGHT} /
     *                  {@link #FLAGS_EXPORT_LEFT} / {@link #FLAGS_EXPORT_RIGHT}
     * @param recordIds 录音 ID 数组（4 字节无符号，用 long 承载）
     */
    public SyncRecordFileIdOpCommand(int flags, long[] recordIds) {
        super();
        if (!isSupportedFlags(flags)) {
            throw new IllegalArgumentException("不支持的 EC D4 flags: 0x"
                    + Integer.toHexString(flags));
        }
        if (recordIds == null || recordIds.length == 0
                || recordIds.length > MAX_BATCH_DELETE_COUNT) {
            throw new IllegalArgumentException("录音 ID 数量超出协议范围（最多 "
                    + MAX_BATCH_DELETE_COUNT + " 个）");
        }
        if ((flags == FLAGS_EXPORT_LEFT || flags == FLAGS_EXPORT_RIGHT)
                && recordIds.length != 1) {
            throw new IllegalArgumentException("按 ID 导出仅支持单个录音 ID");
        }
        for (long id : recordIds) {
            if (id < 0L || id > MAX_UINT32) {
                throw new IllegalArgumentException("录音 ID 超出 uint32 范围");
            }
        }
        this.flags = flags;
        this.recordIds = recordIds.clone();
        setCommandPrefix(COMMAND_PREFIX);
    }

    /**
     * 批量按 ID 删除。
     *
     * @param fileType 1=左耳(0x22)，2=右耳(0x12)
     */
    public static SyncRecordFileIdOpCommand forDelete(long[] recordIds, int fileType) {
        return new SyncRecordFileIdOpCommand(flagsForDelete(fileType), recordIds);
    }

    /**
     * 按 ID 导出（同步）。
     *
     * @param recordId 录音 ID
     * @param fileType 1=左耳(0x21)，2=右耳(0x11)
     */
    public static SyncRecordFileIdOpCommand forExport(long recordId, int fileType) {
        return new SyncRecordFileIdOpCommand(flagsForFileType(fileType), new long[]{recordId});
    }

    public static int flagsForFileType(int fileType) {
        return earBits(fileType) | FLAG_EXPORT;
    }

    public static int flagsForDelete(int fileType) {
        return earBits(fileType) | FLAG_DELETE;
    }

    private static int earBits(int fileType) {
        if (fileType == 1) {
            return FLAG_EAR_LEFT;
        }
        if (fileType == 2) {
            return FLAG_EAR_RIGHT;
        }
        throw new IllegalArgumentException("fileType 仅支持 1(左耳) 或 2(右耳)");
    }

    private static boolean isSupportedFlags(int flags) {
        return flags == FLAGS_DELETE_LEFT
                || flags == FLAGS_DELETE_RIGHT
                || flags == FLAGS_EXPORT_LEFT
                || flags == FLAGS_EXPORT_RIGHT;
    }

    private boolean isDeleteOp() {
        return flags == FLAGS_DELETE_LEFT || flags == FLAGS_DELETE_RIGHT;
    }

    public int getFlags() {
        return flags;
    }

    public long[] getRecordIds() {
        return recordIds.clone();
    }

    @Override
    public String getCommandData() {
        int payloadLen = 1 + recordIds.length * 4;
        StringBuilder sb = new StringBuilder(COMMAND_PREFIX);
        sb.append(String.format("%04X", payloadLen));
        sb.append(String.format("%02X", flags & 0xFF));
        for (long id : recordIds) {
            sb.append(toLittleEndianUint32Hex(id));
        }
        sb.append(COMMAND_SUFFIX);
        String command = sb.toString();
        HsLog.d(TAG, "生成的命令: " + command
                + "，flags=0x" + String.format("%02X", flags)
                + "，数量=" + recordIds.length);
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
            String desc = isDeleteOp() ? "按 ID 删除录音成功" : "按 ID 开始同步录音成功";
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, 0, desc);
        }
        String desc = (isDeleteOp() ? "按 ID 删除录音失败" : "按 ID 开始同步录音失败")
                + "，状态: 0x" + String.format("%02X", status);
        HsLog.w(TAG, desc);
        return completed(ResultCode.FAILED, 1, desc);
    }

    static String toLittleEndianUint32Hex(long value) {
        long v = value & MAX_UINT32;
        return String.format("%02X%02X%02X%02X",
                (int) (v & 0xFF),
                (int) ((v >> 8) & 0xFF),
                (int) ((v >> 16) & 0xFF),
                (int) ((v >> 24) & 0xFF));
    }
}
