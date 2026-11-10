package com.yovo.biz_ggec_hs.internal.protocol.internalcmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.Calendar;

/**
 * 校正 RTC 时间命令（协议 EC C6，SDK 内部使用，不对外暴露）
 * <p>
 * 发送：99 EC C6 00 07 xx xx xx xx xx xx xx 12 34
 * xx xx：年（uint16 大端，如 2026 = 0x07EA）
 * xx：月 / 日 / 时 / 分 / 秒
 * <p>
 * 响应：99 EC C6 00 01 xx 12 34
 * xx：0x00 = 成功    0x01 = 失败
 */
public class RtcCalibrateCommand extends Command {
    private static final String TAG = "RtcCalibrateCommand";

    private static final String COMMAND_PREFIX = "99ECC6";

    /**
     * 响应帧最小长度（chars）：99ECC6(6) + 0001(4) + xx(2) + 1234(4) = 16
     */
    private static final int STATUS_RESPONSE_LENGTH = 16;

    private final String commandData;

    /**
     * 使用当前系统时间构造校准命令
     */
    public RtcCalibrateCommand() {
        this(Calendar.getInstance());
    }

    /**
     * 使用指定时间构造校准命令
     */
    public RtcCalibrateCommand(Calendar calendar) {
        super();
        if (calendar == null) {
            throw new IllegalArgumentException("RTC 校准时间不能为空");
        }
        setCommandPrefix(COMMAND_PREFIX);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        // HEAD=99EC CMD=C6 LEN=0007 DATA=year(2)+month+day+hour+minute+second SEQ=1234
        this.commandData = String.format(
                "99ECC60007%04X%02X%02X%02X%02X%02X",
                year, month, day, hour, minute, second
        ) + "1234";

        HsLog.d(TAG, String.format(
                "构造RTC校准命令: %04d-%02d-%02d %02d:%02d:%02d",
                year, month, day, hour, minute, second
        ));
    }

    @Override
    public String getCommandData() {
        return commandData;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            return false;
        }
        return responseData.length() == STATUS_RESPONSE_LENGTH
                || isExactCommandEcho(responseData);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!isResponseMatch(responseData)) {
            HsLog.e(TAG, "响应数据格式错误，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // Some compatible firmware acknowledges RTC writes by echoing the exact request
        // instead of returning the one-byte status frame. An exact echo is bounded to this
        // command instance, so it cannot consume another RTC attempt's response.
        if (isExactCommandEcho(responseData)) {
            HsLog.i(TAG, "RTC校准结果: 固件回显确认成功");
            return completed(ResultCode.SUCCESS, 0, "RTC时间校准成功");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        String desc;
        switch (status) {
            case 0x00:
                desc = "RTC时间校准成功";
                break;
            case 0x01:
                desc = "RTC时间校准失败";
                break;
            default:
                desc = "未知结果: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "RTC校准结果: 0x" + String.format("%02X", status) + " " + desc);
        return completed(status == 0x00 ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }

    private boolean isExactCommandEcho(String responseData) {
        return responseData != null && commandData.equalsIgnoreCase(responseData);
    }
}
