package com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.Locale;

/**
 * 耳机触控设置命令（协议 EC 85，v11 不区分左右耳）
 * <p>
 * 发送：99 EC 85 00 02 xx 0x 12 34
 * xx: 01=双击, 03=三击（写入固件右耳槽）
 * <p>
 * EC98 KEY_EVENT(0x85) 固件仍是 4 字节 {@code [L双击][R双击][L三击][R三击]}。
 * V11 查询只取右耳双击/三击，映射为 {@code 85_0}/{@code 85_1}。
 * V12 左右耳四槽由 C8 首页快照解析。
 * <p>
 * 响应：固件原样回显 SET 帧
 */
public class ControlCommandV11 extends Command {
    private static final String TAG = "ControlCommandV11";

    private static final String COMMAND_PREFIX = "99EC850002";

    private static final String COMMAND_FORMAT = "99EC850002%02d%02d";

    private static final String COMMAND_SUFFIX = "1234";
    private static final int AGGREGATE_STATUS_LENGTH = 8;

    /** v11 触击类型：双击 */
    public static final int TAP_TWICE = 0;
    /** v11 触击类型：三击 */
    public static final int TAP_TRIPLE = 1;

    private static final String[] FUNCTION_NAMES = {
            "播放/暂停",
            "音量加",
            "音量减",
            "上一曲",
            "下一曲",
            "语音助手",
            "EQ切换",
            "游戏模式",
            "无"
    };

    private final int tapType;
    private final int functionType;

    /**
     * @param tapType      触击类型（{@link #TAP_TWICE}=0, {@link #TAP_TRIPLE}=1）
     * @param functionType 功能类型
     */
    public ControlCommandV11(int tapType, int functionType) {
        super();
        if ((tapType != TAP_TWICE && tapType != TAP_TRIPLE)
                || functionType < 0
                || functionType >= FUNCTION_NAMES.length) {
            throw new IllegalArgumentException("v11 触控设置参数超出协议范围");
        }
        this.tapType = tapType;
        this.functionType = functionType;
        setCommandPrefix("99EC85");
    }

    @Override
    public String getCommandData() {
        int position = calculatePosition();
        return String.format(COMMAND_FORMAT, position, functionType) + COMMAND_SUFFIX;
    }

    private int calculatePosition() {
        if (tapType == TAP_TRIPLE) {
            return 3;
        }
        return 1;
    }

    /**
     * 解析 EC98 中 V11 的 EC85 状态。
     * 固件 4 字节为 {@code [L双击][R双击][L三击][R三击]}；V11 只取右耳双击/三击。
     *
     * @return {@code [双击功能, 三击功能]}
     */
    public static String[] parseAggregateFunctions(String keyControlData) {
        if (keyControlData == null || keyControlData.length() < AGGREGATE_STATUS_LENGTH) {
            throw new IllegalArgumentException("V11 EC85 聚合状态数据长度不足");
        }
        return new String[]{
                keyControlData.substring(2, 4).toUpperCase(Locale.US),
                keyControlData.substring(6, 8).toUpperCase(Locale.US)
        };
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        boolean isSuccess = responseData != null
                && getCommandData().equalsIgnoreCase(responseData);

        String functionName = (functionType >= 0 && functionType < FUNCTION_NAMES.length)
                ? FUNCTION_NAMES[functionType] : "未知功能";

        String tapName = (tapType == TAP_TWICE) ? "双击" : "三击";

        String result = isSuccess
                ? tapName + "设置为\"" + functionName + "\"成功"
                : tapName + "设置为\"" + functionName + "\"失败";

        int resultValue = isSuccess ? functionType : 0;

        return completed(
                isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED,
                resultValue,
                result);
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (responseData != null && responseData.length() >= 10) {
            String prefix = responseData.substring(0, 10);
            return COMMAND_PREFIX.equals(prefix);
        }
        return false;
    }
}
