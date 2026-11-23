package com.yovo.biz_ggec_hs.internal.center.command;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.DeleteRecordFileCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;

/**
 * 协议命令唯一构造入口。
 *
 * <p>录音子系统与引擎补查原先在 {@code internal/record} 与 {@code HsProtocolEngine}
 * 各自 {@code new} 协议命令。此处把「外部构造」收敛为静态工厂，调用点只表达意图，
 * 命令的具体类型和参数细节集中在本类。方法体仅做 {@code return new ...}，不含任何业务逻辑。</p>
 *
 * <p>能力→命令的合法映射点仍是 {@code internal/interact/HsCapabilityRouter}，不在本类范围内。</p>
 */
public final class HsCommandFactory {

    private HsCommandFactory() {
        // 纯静态工厂，禁止实例化。
    }

    // ── 录音：本机 Flash 录音 ──────────────────────────────────────────────

    /** 开始本地录音（EC BB）。 */
    @NonNull
    public static StartLocalRecordCommand startLocalRecord() {
        return new StartLocalRecordCommand();
    }

    /** 结束本地录音（EC BC）。 */
    @NonNull
    public static StopLocalRecordCommand stopLocalRecord() {
        return new StopLocalRecordCommand();
    }

    /** 获取录音文件列表（EC C0）：{@code page} 为页数（skipNum），{@code limit} 为返回数量。 */
    @NonNull
    public static GetRecordIdListCommand getRecordIdList(int page, int limit) {
        return new GetRecordIdListCommand(page, limit);
    }

    /** 批量删除录音文件（EC C1）：传入文件索引数组。 */
    @NonNull
    public static DeleteRecordFileCommand deleteRecordFile(int[] fileIndices) {
        return new DeleteRecordFileCommand(fileIndices);
    }

    // ── 录音：设备文件同步传输 ────────────────────────────────────────────

    /** 开始同步录音文件（EC C4）：{@code fileIndex} 为文件索引。 */
    @NonNull
    public static StartTransmitRecordCommand startTransmitRecord(int fileIndex) {
        return new StartTransmitRecordCommand(fileIndex);
    }

    /** 取消传输录音文件（EC C5）。 */
    @NonNull
    public static CancelTransmitRecordCommand cancelTransmitRecord() {
        return new CancelTransmitRecordCommand();
    }

    // ── 录音：随录随传 ───────────────────────────────────────────────────

    /** 开始本地录音随录随传（EC D5）。 */
    @NonNull
    public static StartStreamRecordCommand startStreamRecord() {
        return new StartStreamRecordCommand();
    }

    /** 停止本地录音随录随传（EC D6）。 */
    @NonNull
    public static StopStreamRecordCommand stopStreamRecord() {
        return new StopStreamRecordCommand();
    }

    // ── 耳机颜色 ────────────────────────────────────────────────────────

    /** 查询耳机颜色（99 EC 91）：{@code origin} 决定响应等待。 */
    @NonNull
    public static EarColorCommand earColorQuery(@NonNull EarColorCommand.QueryOrigin origin) {
        return new EarColorCommand(origin);
    }
}
