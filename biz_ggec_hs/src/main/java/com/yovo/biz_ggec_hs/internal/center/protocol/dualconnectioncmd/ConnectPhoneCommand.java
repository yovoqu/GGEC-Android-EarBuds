package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-10
 * Description:
 * 连接指定手机命令（协议 EC AB）
 * <p>
 * 发送：99 EC AB 00 07 [保留 1字节] [MAC 6字节] 12 34
 * 响应：99 EC AB 00 07 [STATUS 1字节] [MAC 6字节] 12 34
 * <p>
 * 保留字节默认 0x00，设备侧默认最大连接超时 10s。
 * <p>
 * STATUS 字节含义：
 * 0x00 = 连接成功
 * 0x01 = 耳机端连接已超数
 * 0x02 = 连接超时
 * <p>
 * SDK 兜底超时 {@link #TIMEOUT_MS}：12s（设备侧超时 10s，SDK 留 2s 余量）。
 */
public class ConnectPhoneCommand extends Command {
    private static final String TAG = "ConnectPhoneCommand";

    private static final String COMMAND_PREFIX = "99ECAB";

    /** SDK 命令超时：12s（兜底，保证能收到设备侧 10s 超时响应） */
    private static final long TIMEOUT_MS = 12_000L;

    /** 响应帧最小长度（chars）：99ECAB(6) + 0007(4) + MAC(12) + STATUS(2) + 1234(4) = 28 */
    private static final int MIN_RESPONSE_LENGTH = 28;

    private final String commandData;

    /**
     * @param macStr 目标手机蓝牙 MAC 地址，格式 XX:XX:XX:XX:XX:XX 或 XXXXXXXXXXXX
     */
    public ConnectPhoneCommand(String macStr) {
        super();
        setCommandPrefix(COMMAND_PREFIX);
        this.commandData = buildCommandData(macStr);
    }

    private static String buildCommandData(String macStr) {
        String macHex = ProtocolValues.normalizeMacAddress(macStr);
        if (macHex == null) {
            throw new IllegalArgumentException("目标手机 MAC 地址格式无效");
        }
        // HEAD=99EC CMD=AB LEN=0007 DATA=保留(1byte=00)+MAC(6bytes) SEQ=1234
        return "99ECAB0007" + "00" + macHex + "1234";
    }

    @Override
    public String getCommandData() {
        return commandData;
    }

    @Override
    public long getTimeoutMs() {
        return TIMEOUT_MS;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != MIN_RESPONSE_LENGTH
                || !responseData.regionMatches(true, 12, commandData, 12, 12)) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: " + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]  99ECAB  CMD
        //   [6..9]  0007    LEN
        //   [10..11] STATUS 1 字节 ← 连接结果
        //   [12..23] MAC    6 字节
        //   [24..27] 1234   SEQ
        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态解析失败");
        }

        String desc;
        switch (status) {
            case 0x00:
                desc = "连接成功";
                break;
            case 0x01:
                desc = "耳机端连接已超数";
                break;
            case 0x02:
                desc = "连接超时";
                break;
            default:
                desc = "连接结果: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "连接结果，状态=0x" + String.format("%02X", status) + "，说明=" + desc);
        return completed(status == 0x00 ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }
}
