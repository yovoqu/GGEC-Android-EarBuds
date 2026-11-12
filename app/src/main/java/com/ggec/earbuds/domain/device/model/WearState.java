package com.ggec.earbuds.domain.device.model;

/**
 * 佩戴状态。
 *
 * <p>{@link #UNKNOWN} 与 {@link #NOT_WORN} 是两件事：前者是「没有这项数据」，后者是「确知未佩戴」。
 * 协议只在「耳机不在耳朵上」这一侧给出证据：设备主动上报的两耳都在仓（{@code 99EC96} 状态 3）
 * 意味着耳机在充电盒里，因此 {@link #NOT_WORN} 只由该状态推出。</p>
 *
 * <p>「左/右/双耳佩戴中」当前没有数据来源：有耳出仓只说明它离开了充电盒，也可能放在桌上，
 * 协议没有任何一帧能证明耳机戴在耳朵上；佩戴检测服务的状态码 {@code B5_5} 是功能开关，不是佩戴态。
 * 因此本枚举不预设这些取值，协议补齐后再引入，不用相邻状态推测。</p>
 */
public enum WearState {
    UNKNOWN,
    NOT_WORN
}
