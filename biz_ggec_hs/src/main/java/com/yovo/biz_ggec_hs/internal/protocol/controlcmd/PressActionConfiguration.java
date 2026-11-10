package com.yovo.biz_ggec_hs.internal.protocol.controlcmd;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 长触/短触/双触查询返回的左右耳完整配置。
 */
public final class PressActionConfiguration {
    public static final int EAR_BOTH = 0;
    public static final int EAR_LEFT = 1;
    public static final int EAR_RIGHT = 2;

    /** 长触：0~4。 */
    public static final int LONG_ACTION_MAX = 4;
    /** 单触：0~7。 */
    public static final int SHORT_ACTION_MAX = 7;
    /** 双触：0~8。 */
    public static final int DOUBLE_ACTION_MAX = 8;

    /** 协议左耳编号。 */
    public static final String KEY_LEFT_EAR = "01";
    /** 协议右耳编号。 */
    public static final String KEY_RIGHT_EAR = "02";
    /** 设置命令回显键：操作类型。 */
    public static final String KEY_ACTION_TYPE = "actionType";
    /** 设置命令回显键：耳类型（0=左右耳, 1=左耳, 2=右耳）。 */
    public static final String KEY_EAR_TYPE = "earType";

    private final int leftEarType;
    private final int leftAction;
    private final int rightEarType;
    private final int rightAction;

    private PressActionConfiguration(int leftEarType, int leftAction,
                                      int rightEarType, int rightAction) {
        this.leftEarType = leftEarType;
        this.leftAction = leftAction;
        this.rightEarType = rightEarType;
        this.rightAction = rightAction;
    }

    static PressActionConfiguration parseLong(String responseData) {
        return parse(responseData, LONG_ACTION_MAX);
    }

    static PressActionConfiguration parseShort(String responseData) {
        return parse(responseData, SHORT_ACTION_MAX);
    }

    static PressActionConfiguration parseDouble(String responseData) {
        return parse(responseData, DOUBLE_ACTION_MAX);
    }

    private static PressActionConfiguration parse(String responseData, int maxActionInclusive) {
        int leftEar = parseByte(responseData, 10);
        int leftAction = parseByte(responseData, 12);
        int rightEar = parseByte(responseData, 14);
        int rightAction = parseByte(responseData, 16);
        if (leftEar != EAR_LEFT || rightEar != EAR_RIGHT) {
            throw new IllegalArgumentException("左右耳编号不符合协议");
        }
        if (!isActionValid(leftAction, maxActionInclusive)
                || !isActionValid(rightAction, maxActionInclusive)) {
            throw new IllegalArgumentException("触控操作类型超出协议范围");
        }
        return new PressActionConfiguration(leftEar, leftAction, rightEar, rightAction);
    }

    public int getLeftEarType() {
        return leftEarType;
    }

    public int getLeftAction() {
        return leftAction;
    }

    public int getRightEarType() {
        return rightEarType;
    }

    public int getRightAction() {
        return rightAction;
    }

    /**
     * 查询结果 Map：{@code 01}=左耳功能码，{@code 02}=右耳功能码，与固件回包一一对应。
     */
    public Map<String, Integer> toMap() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(KEY_LEFT_EAR, leftAction);
        result.put(KEY_RIGHT_EAR, rightAction);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 设置命令的回显 Map：{@code actionType + earType}。
     */
    public static Map<String, Integer> setResultMap(int actionType, int earType) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(KEY_ACTION_TYPE, actionType);
        result.put(KEY_EAR_TYPE, earType);
        return Collections.unmodifiableMap(result);
    }

    private static int parseByte(String data, int offset) {
        return Integer.parseInt(data.substring(offset, offset + 2), 16);
    }

    private static boolean isActionValid(int action, int maxActionInclusive) {
        return action >= 0 && action <= maxActionInclusive;
    }
}
