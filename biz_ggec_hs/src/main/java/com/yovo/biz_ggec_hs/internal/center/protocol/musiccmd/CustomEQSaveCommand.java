package com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.nio.charset.StandardCharsets;

/**
 * 保存耳机自定义EQ模式参数
 * 发送：99 EC B1 00 [len] [10个频段gain值] [自定义名称bytes] 12 34
 *   len = 频段(10字节) + 名称字节数
 * 响应：99 EC B1 00 [len] [status] [10个频段gain值] [自定义名称bytes] 12 34
 *   len = 状态码(1字节) + 频段(10字节) + 名称字节数
 *   status: 0x00=成功, 0x01=数据异常
 */
public class CustomEQSaveCommand extends Command {
    private static final String TAG = "CustomEQSaveCmd";
    private static final String COMMAND_PREFIX_STR = "99ECB1";
    private static final String COMMAND_SUFFIX = "1234";

    private final int[] gains;
    private final String name;

    /**
     * @param gains 10个频段增益值数组，每个值范围 0x00~0x18
     * @param name  自定义参数名，UTF-8编码不超过20字节
     */
    public CustomEQSaveCommand(int[] gains, String name) {
        super();
        if (gains == null || gains.length != CustomEQRealtimeCommand.BAND_COUNT) {
            throw new IllegalArgumentException("需要" + CustomEQRealtimeCommand.BAND_COUNT + "个频段增益值");
        }
        for (int gain : gains) {
            if (gain < CustomEQRealtimeCommand.GAIN_MIN
                    || gain > CustomEQRealtimeCommand.GAIN_MAX) {
                throw new IllegalArgumentException("自定义 EQ 增益值超出协议范围");
            }
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("自定义参数名不能为空");
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 20) {
            throw new IllegalArgumentException("自定义参数名UTF-8编码不能超过20字节");
        }
        this.gains = gains.clone();
        this.name = name;
        setCommandPrefix(COMMAND_PREFIX_STR);
    }

    @Override
    public String getCommandData() {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        int dataLength = CustomEQRealtimeCommand.BAND_COUNT + nameBytes.length;

        StringBuilder sb = new StringBuilder();
        sb.append(COMMAND_PREFIX_STR);
        sb.append(String.format("%04X", dataLength));
        for (int gain : gains) {
            sb.append(String.format("%02X", gain));
        }
        for (byte b : nameBytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        sb.append(COMMAND_SUFFIX);
        return sb.toString();
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应: " + responseData);
        String request = getCommandData();
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX_STR)
                || responseData.length() != request.length() + 2
                || !responseData.regionMatches(
                        true, 12, request, 10, request.length() - 14)) {
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        try {
            int status = Integer.parseInt(responseData.substring(10, 12), 16);
            if (status != 0x00 && status != 0x01) {
                return completed(ResultCode.FAILED, status, "自定义EQ保存状态超出协议范围");
            }
            boolean isSuccess = (status == 0x00);
            String result = isSuccess ? "自定义EQ保存成功" : "自定义EQ保存失败：数据异常";
            return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, status, result);
        } catch (Exception e) {
            HsLog.e(TAG, "解析保存响应失败", e);
            return completed(ResultCode.FAILED, 0, "解析保存响应失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.length() >= 6
                && COMMAND_PREFIX_STR.equals(responseData.substring(0, 6));
    }
}
