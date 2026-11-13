package com.yovo.biz_ggec_hs.internal.center.protocol.internalcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 离线 log 开关命令（协议 EC D3，SDK 内部 / Debug 使用）。
 * <p>
 * 关闭：
 * 发送：99 EC D3 00 01 00 12 34
 * <p>
 * 开启（载荷为 01 + 4 字节 log 空间大小，大端序，最大 8M）：
 * 发送：99 EC D3 00 05 01 xx xx xx xx 12 34
 * <p>
 * 响应：99 EC D3 00 01 00 12 34（成功）/ 99 EC D3 00 01 01 12 34（失败）
 */
public class OfflineLogCommand extends Command {
    private static final String TAG = "OfflineLogCommand";

    private static final String COMMAND_PREFIX = "99ECD3";
    private static final String COMMAND_SUFFIX = "1234";

    /** log 空间大小上限：8M（字节）。 */
    public static final int MAX_LOG_SPACE_BYTES = 8 * 1024 * 1024;

    /** 响应帧长度（chars）：99ECD3(6) + 0001(4) + status(2) + 1234(4) = 16 */
    private static final int STATUS_RESPONSE_LENGTH = 16;

    private final boolean enabled;
    private final int logSpaceBytes;

    /**
     * @param enabled        true=开启离线 log，false=关闭
     * @param logSpaceBytes  开启时分配的 log 空间大小（字节，0~8M）；关闭时忽略
     */
    public OfflineLogCommand(boolean enabled, int logSpaceBytes) {
        super();
        if (logSpaceBytes < 0) {
            throw new IllegalArgumentException("log 空间大小不能为负数");
        }
        if (enabled && logSpaceBytes > MAX_LOG_SPACE_BYTES) {
            throw new IllegalArgumentException(
                    "log 空间大小不能超过 8M，实际: " + logSpaceBytes);
        }
        this.enabled = enabled;
        this.logSpaceBytes = logSpaceBytes;
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        if (!enabled) {
            return COMMAND_PREFIX + "000100" + COMMAND_SUFFIX;
        }
        return COMMAND_PREFIX + "000501"
                + String.format("%08X", logSpaceBytes)
                + COMMAND_SUFFIX;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            return false;
        }
        return responseData.length() == STATUS_RESPONSE_LENGTH;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!isResponseMatch(responseData)) {
            HsLog.e(TAG, "响应数据格式错误，实际长度: "
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

        String action = enabled ? "开启" : "关闭";
        switch (status) {
            case 0x00:
                HsLog.i(TAG, "离线log" + action + "成功");
                return completed(ResultCode.SUCCESS, status, "离线log" + action + "成功");
            case 0x01:
                HsLog.w(TAG, "离线log" + action + "失败");
                return completed(ResultCode.FAILED, status, "离线log" + action + "失败");
            default:
                HsLog.w(TAG, "离线log状态异常，status=0x"
                        + String.format("%02X", status));
                return completed(ResultCode.FAILED, status,
                        "离线log状态异常: 0x" + String.format("%02X", status));
        }
    }
}
