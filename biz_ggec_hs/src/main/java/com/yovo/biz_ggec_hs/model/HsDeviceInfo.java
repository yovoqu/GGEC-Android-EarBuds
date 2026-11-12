package com.yovo.biz_ggec_hs.model;

import java.util.Objects;

/**
 * 设备信息快照：固件版本、耳机颜色与耳机在仓状态。
 *
 * <p>来源各自独立，互不替代：</p>
 * <ul>
 *   <li>{@link #firmware()} 来自首页聚合状态命令（{@code 99ECC7}）回包的 TLV {@code 0x94}，
 *       按 UTF-8 文本解码；设备未回报时取 {@link #UNKNOWN_FIRMWARE}。</li>
 *   <li>{@link #earColor()} 来自耳机颜色命令（{@code 99EC91}）回包的颜色字节，
 *       原样透传设备值，不做范围映射；设备未回报时取 {@link #UNKNOWN_EAR_COLOR}。</li>
 *   <li>{@link #earInCaseStatus()} 来自设备主动上报的在仓状态帧（{@code 99EC96}），
 *       取值 0–3 原样透传；设备未上报时取 {@link #UNKNOWN_EAR_IN_CASE}。</li>
 * </ul>
 *
 * <p>三项都不保证一次会话内必到，因此用空串与负值表达「未知」，不用 null。</p>
 */
public record HsDeviceInfo(String firmware, int earColor, int earInCaseStatus) {

    /** 设备尚未回报固件版本。 */
    public static final String UNKNOWN_FIRMWARE = "";

    /** 设备尚未回报耳机颜色。协议颜色字节为正整数，负值不会与真实值冲突。 */
    public static final int UNKNOWN_EAR_COLOR = -1;

    /** 设备尚未上报耳机在仓状态。协议状态值为 0–3，负值不会与真实值冲突。 */
    public static final int UNKNOWN_EAR_IN_CASE = -1;

    /** 两耳都在充电盒内。协议状态值：0 都不在仓，1 仅左耳，2 仅右耳，3 两耳都在仓。 */
    public static final int EAR_IN_CASE_BOTH = 3;

    public HsDeviceInfo {
        firmware = Objects.requireNonNull(firmware, "firmware");
    }

    public boolean hasFirmware() {
        return !firmware.isEmpty();
    }

    public boolean hasEarColor() {
        return earColor >= 0;
    }
}
