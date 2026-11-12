package com.ggec.earbuds.ui.device;

/**
 * 一次控件写入的落地结果，按能力 id 回传视图层。
 *
 * <p>视图用它决定「未确认」呈现去留：成功则保留用户本次选择（设备没有 Query 时无法回读），
 * 失败则丢弃该选择、回到未确认呈现，不假装成功。失败的用户文案仍由
 * {@link DeviceFailureText} 单独给出。</p>
 */
public record DeviceControlOutcome(String capabilityId, boolean succeeded) {

    public static DeviceControlOutcome ok(String capabilityId) {
        return new DeviceControlOutcome(capabilityId, true);
    }

    public static DeviceControlOutcome failed(String capabilityId) {
        return new DeviceControlOutcome(capabilityId, false);
    }
}
