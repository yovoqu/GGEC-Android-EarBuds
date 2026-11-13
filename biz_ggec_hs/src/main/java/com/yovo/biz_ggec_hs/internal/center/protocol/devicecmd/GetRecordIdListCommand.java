package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;



import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolRecording;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取耳机录音列表命令（协议 EC C0）。
 * <p>
 * 发送：99 EC C0 00 02 xx xx 12 34
 *   bytes[5]：页数（skipNum，路径下的第几页）
 *   bytes[6]：返回文件数量（外部传入 1~12 时，SDK 统一按 12 下发给设备）
 *   特例：skipNum=0 且 limitNum=0 → 返回当前正在录音文件（0/1 条）
 * <p>
 * 成功响应：99 EC C0 00 xx 00 [records...] 12 34
 *   bytes[3..4]：数据长度（含 1 字节状态）
 *   bytes[5]：状态 0x00=成功
 *   bytes[6]：文件数量
 *   bytes[7..n]：文件记录列表，每条固定 20 字节：
 *     创建时间 8 字节（年 uint16 小端 + 月 + 日 + 周 + 时 + 分 + 秒）
 *     + 录音索引 1 字节 + 录音类型 1 字节 + 录音时长 2 字节（小端）
 *     + 录音大小 4 字节（小端） + 录音 ID 4 字节（小端）
 * <p>
 * 对外契约只需要「文件标识 / 名称 / 时长 / 大小」四项：文件标识取协议录音索引
 * （传输与删除命令都用它定位文件），名称在没有文件名的固件里与索引同源。
 * <p>
 * 失败响应：99 EC C0 00 02 01 00 12 34
 *   bytes[5]：状态 0x01=失败
 */
public class GetRecordIdListCommand extends Command {
    private static final String TAG = "GetRecordIdListCommand";

    private static final String COMMAND_PREFIX = "99ECC0";
    private static final String COMMAND_SUFFIX = "1234";

    /** 每页最多返回的文件数量（固件限定） */
    private static final int MAX_PAGE_SIZE = 12;

    /** 单条记录字节数：时间(8)+索引(1)+类型(1)+时长(2)+大小(4)+ID(4) */
    private static final int RECORD_BYTES = 20;

    /** 创建时间区字节数：年(2)+月+日+周+时+分+秒 */
    private static final int TIME_BYTES = 8;

    /** 单条记录占用的 hex 字符数 */
    private static final int RECORD_HEX_LEN = RECORD_BYTES * 2;

    /**
     * 响应帧最小长度（chars）：
     * 99ECC0(6) + 00(2) + len(2) + status(2) + count(2) + 1234(4) = 18
     */
    private static final int MIN_RESPONSE_LENGTH = 18;

    private final int skipNum;
    private final int limitNum;

    public GetRecordIdListCommand(int skipNum, int limitNum) {
        super();
        // 允许 (0,0) 查正在录音；其余外部 limit 须在 1~12（实际下发统一为 12）
        boolean currentRec = skipNum == 0 && limitNum == 0;
        if (skipNum < 0 || skipNum > 0xFF
                || (!currentRec && (limitNum < 1 || limitNum > MAX_PAGE_SIZE))) {
            throw new IllegalArgumentException("录音列表分页参数超出协议范围");
        }
        this.skipNum = skipNum;
        this.limitNum = limitNum;
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        // 99ECC0 + 0002(LEN=2) + skip(1) + limit(1) + 1234
        // 外部 limit 1~12 统一按 12 发给设备；(0,0) 特殊查询保持 0
        int wireLimit = (skipNum == 0 && limitNum == 0) ? 0 : 12;
        String command = COMMAND_PREFIX + "0002"
                + String.format("%02X", skipNum & 0xFF)
                + String.format("%02X", wireLimit & 0xFF)
                + COMMAND_SUFFIX;
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
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "响应数据格式错误");
        }

        // 响应帧布局（hex 字符索引）：
        //   [0..5]   99ECC0     CMD prefix
        //   [6..9]   xxxx       数据长度（含状态字节）
        //   [10..11] xx         状态字节（00=成功，01=失败）
        //   [12..13] xx         文件数量
        //   [14..len-4]         文件记录（每条 20 字节）
        //   [len-4..len-1]      1234 SEQ

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "状态字节解析失败");
        }

        if (status != 0x00) {
            HsLog.w(TAG, "获取录音列表失败，状态: 0x" + String.format("%02X", status));
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "获取录音列表失败");
        }

        int fileCount;
        try {
            fileCount = Integer.parseInt(responseData.substring(12, 14), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析文件数量失败", e);
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "文件数量解析失败");
        }

        int recordDataLength = responseData.length() - 18;
        // (0,0) 正在录音：最多 1 条；普通分页实际按 12 下发，
        // 设备返回 0~12 条均接受，不再与外部 limitNum 比对
        int maxAllowed = (skipNum == 0 && limitNum == 0) ? 1 : MAX_PAGE_SIZE;
        if (fileCount > maxAllowed || recordDataLength != fileCount * RECORD_HEX_LEN) {
            HsLog.e(TAG, "录音列表数量与数据长度不一致");
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "录音列表数据格式错误");
        }

        List<ProtocolRecording> records = parseRecords(responseData, fileCount);
        if (records.size() != fileCount) {
            return completed(ResultCode.FAILED, new ArrayList<ProtocolRecording>(), "录音列表记录解析不完整");
        }

        String desc = "获取录音列表成功: " + records.size() + " 个文件";
        HsLog.i(TAG, desc + "，记录=" + records);
        return completed(ResultCode.SUCCESS, records, desc);
    }

    /**
     * 解析文件记录区，每条固定 20 字节。
     *
     * @param responseData 完整响应帧（hex）
     * @param fileCount    协议声明的文件数量
     * @return 录音文件信息列表
     */
    private List<ProtocolRecording> parseRecords(String responseData, int fileCount) {
        List<ProtocolRecording> records = new ArrayList<>();
        if (fileCount <= 0) {
            return records;
        }

        // 记录区：从 [14] 起，到帧尾 SEQ(1234) 之前
        int dataStart = 14;
        int dataEnd = responseData.length() - COMMAND_SUFFIX.length();
        if (dataEnd <= dataStart) {
            HsLog.w(TAG, "记录数据区为空");
            return records;
        }

        String recordsHex = responseData.substring(dataStart, dataEnd);

        for (int i = 0; i < fileCount; i++) {
            int off = i * RECORD_HEX_LEN;
            if (off + RECORD_HEX_LEN > recordsHex.length()) {
                HsLog.w(TAG, "记录数据越界，已解析 " + records.size()
                        + " 条，期望 " + fileCount + " 条");
                break;
            }

            try {
                ProtocolRecording record = parseOneRecord(recordsHex, off);
                HsLog.i(TAG, "EC 获取到文件列表=" + record);
                records.add(record);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "解析第 " + i + " 条录音记录失败", e);
            }
        }

        return records;
    }

    /**
     * 解析单条 20 字节录音记录。
     */
    private ProtocolRecording parseOneRecord(String recordsHex, int charOffset) {
        int businessOffset = charOffset + TIME_BYTES * 2;
        int index = hexByte(recordsHex, businessOffset);
        int duration = readUInt16LittleEndian(recordsHex, businessOffset + 4);
        long size = readUInt32LittleEndian(recordsHex, businessOffset + 8);
        String fileId = String.valueOf(index);
        return new ProtocolRecording(fileId, fileId, duration, size);
    }

    private static int hexByte(String hex, int charOffset) {
        return Integer.parseInt(hex.substring(charOffset, charOffset + 2), 16);
    }

    private static int readUInt16LittleEndian(String hex, int charOffset) {
        return hexByte(hex, charOffset)
                | (hexByte(hex, charOffset + 2) << 8);
    }

    private static long readUInt32LittleEndian(String hex, int charOffset) {
        return hexByte(hex, charOffset)
                | ((long) hexByte(hex, charOffset + 2) << 8)
                | ((long) hexByte(hex, charOffset + 4) << 16)
                | ((long) hexByte(hex, charOffset + 6) << 24);
    }
}
