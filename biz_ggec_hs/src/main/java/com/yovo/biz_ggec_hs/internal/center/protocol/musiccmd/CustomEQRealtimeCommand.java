package com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 实时设置耳机自定义EQ模式参数
 * 协议：99 EC B0 00 0A [10个频段gain值] 12 34
 * 频段：60Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 12kHz, 16kHz
 * 增益范围：0x00(-6dB) ~ 0x18(6dB)，步进0.5dB
 */
public class CustomEQRealtimeCommand extends Command {
    private static final String TAG = "CustomEQRealtimeCmd";
    private static final String COMMAND_PREFIX_STR = "99ECB0";
    private static final String COMMAND_SUFFIX = "1234";

    public static final int BAND_COUNT = 10;
    public static final int GAIN_MIN = 0x00;
    public static final int GAIN_MAX = 0x18;
    /** 0 dB。名称首字节被误读成 16K 增益时，回收后回填这一档。 */
    public static final int GAIN_NEUTRAL = 0x0C;

    private final int[] gains;

    /**
     * @param gains 10个频段增益值数组，每个值范围 0x00~0x18
     */
    public CustomEQRealtimeCommand(int[] gains) {
        super();
        if (gains == null || gains.length != BAND_COUNT) {
            throw new IllegalArgumentException("需要" + BAND_COUNT + "个频段增益值");
        }
        for (int gain : gains) {
            if (gain < GAIN_MIN || gain > GAIN_MAX) {
                throw new IllegalArgumentException("增益值范围为0x00-0x18，当前值: " + gain);
            }
        }
        this.gains = gains.clone();
        setCommandPrefix(COMMAND_PREFIX_STR);
    }

    @Override
    public String getCommandData() {
        StringBuilder sb = new StringBuilder();
        sb.append(COMMAND_PREFIX_STR);
        sb.append("000A");
        for (int gain : gains) {
            sb.append(String.format("%02X", gain));
        }
        sb.append(COMMAND_SUFFIX);
        return sb.toString();
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应: " + responseData);
        if (responseData == null || !getCommandData().equalsIgnoreCase(responseData)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }
        return completed(ResultCode.SUCCESS, 0, "自定义EQ实时设置成功");
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.length() >= 6
                && COMMAND_PREFIX_STR.equals(responseData.substring(0, 6));
    }
}
