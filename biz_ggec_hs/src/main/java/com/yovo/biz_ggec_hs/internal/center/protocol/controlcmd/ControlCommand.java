package com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 耳机触控设置命令（协议 EC 85，v12 区分左右耳）
 * <p>
 * 发送：99 EC 85 00 02 xx 0x 12 34
 * xx: 00=左耳双击, 01=右耳双击, 02=左耳三击, 03=右耳三击
 * <p>
 * 功能码分表：
 * 双击 0播放/暂停 1音量加 2音量减 3上一曲 4下一曲 5无
 * 三击 0录音 1语音助手 2EQ切换 3游戏模式 4上一首 5下一首 6音量加 7音量减 8无
 * <p>
 * 响应：固件原样回显 SET 帧（xx 与发送一致，00/01/02/03）
 */
public class ControlCommand extends Command {
    private static final String TAG = "ControlCommand";

    private static final String COMMAND_PREFIX = "99EC850002";

    private static final String COMMAND_FORMAT = "99EC850002%02X%02X";

    private static final String COMMAND_SUFFIX = "1234";

    // 与 YoCommandApi 保持一致
    public static final int EAR_LEFT = 1;
    public static final int EAR_RIGHT = 2;
    public static final int TAP_TWICE = 1;
    public static final int TAP_TRIPLE = 2;

    private static final String[] DOUBLE_FUNCTION_NAMES = {
            "播放/暂停",
            "音量加",
            "音量减",
            "上一曲",
            "下一曲",
            "无"
    };

    private static final String[] TRIPLE_FUNCTION_NAMES = {
            "录音",
            "语音助手",
            "EQ切换",
            "游戏模式",
            "上一首",
            "下一首",
            "音量加",
            "音量减",
            "无"
    };

    private final int earSide;
    private final int tapType;
    private final int functionType;

    /**
     * @param earSide      耳机侧（EAR_LEFT=1, EAR_RIGHT=2）
     * @param tapType      触击类型（TAP_TWICE=1, TAP_TRIPLE=2）
     * @param functionType 功能类型（按双击/三击分表）
     */
    public ControlCommand(int earSide, int tapType, int functionType) {
        super();
        if ((earSide != EAR_LEFT && earSide != EAR_RIGHT)
                || (tapType != TAP_TWICE && tapType != TAP_TRIPLE)
                || !isFunctionAllowed(tapType, functionType)) {
            throw new IllegalArgumentException("触控设置参数超出协议范围");
        }
        this.earSide = earSide;
        this.tapType = tapType;
        this.functionType = functionType;
        setCommandPrefix("99EC85");
    }

    public static boolean isFunctionAllowed(int tapType, int functionType) {
        String[] names = functionNames(tapType);
        return names != null && functionType >= 0 && functionType < names.length;
    }

    private static String[] functionNames(int tapType) {
        if (tapType == TAP_TWICE) {
            return DOUBLE_FUNCTION_NAMES;
        }
        if (tapType == TAP_TRIPLE) {
            return TRIPLE_FUNCTION_NAMES;
        }
        return null;
    }

    @Override
    public String getCommandData() {
        int position = calculateSendPosition();
        return String.format(COMMAND_FORMAT, position, functionType) + COMMAND_SUFFIX;
    }

    private int calculateSendPosition() {
        if (tapType == TAP_TWICE) {
            return earSide == EAR_LEFT ? 0x00 : 0x01;
        } else if (tapType == TAP_TRIPLE) {
            return earSide == EAR_LEFT ? 0x02 : 0x03;
        }
        return 0x00;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        boolean isSuccess = responseData != null
                && getCommandData().equalsIgnoreCase(responseData);

        String[] names = functionNames(tapType);
        String functionName = (names != null && functionType >= 0 && functionType < names.length)
                ? names[functionType] : "未知功能";

        String earName = (earSide == EAR_LEFT) ? "左耳" : "右耳";
        String tapName = (tapType == TAP_TWICE) ? "双击" : "三击";

        String result = isSuccess
                ? earName + tapName + "设置为\"" + functionName + "\"成功"
                : earName + tapName + "设置为\"" + functionName + "\"失败";

        int resultValue = isSuccess ? functionType : 0;

        return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
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
