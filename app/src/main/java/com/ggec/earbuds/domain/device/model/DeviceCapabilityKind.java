package com.ggec.earbuds.domain.device.model;

/**
 * 控件类型：界面按该值决定生成开关、滑块、选择器、曲线编辑器、动作按钮、列表，还是只做门控。
 *
 * <p>由 {@code biz_ggec_hs} 的控制种类映射而来，单点在
 * {@code provider/biz_ggec_hs/HsValueMapper}。APP 不做「能力名 → 控件类型」的推导。</p>
 *
 * <p>呈现语义（界面按此渲染，不再自行判断）：</p>
 * <ul>
 *   <li>{@link #GATE}：只做功能门控（{@code DeviceProfileView.supports(id)}），
 *       <b>不生成任何通用行</b>；OTA 这类由其他模块承载。</li>
 *   <li>{@link #LEVEL}：可写控件，带 {@code range}；值未知仍渲染控件（同 {@link #TOGGLE}），
 *       显示「未确认位置」，写入失败时回退显示。</li>
 *   <li>{@link #LIST}：列表卡，数据来自 {@code DeviceSnapshot} 的列表字段（双连接设备、录音文件）。
 *       注意：双连接的<b>开关</b>是 {@link #TOGGLE}，设备列表是独立的 LIST 能力。</li>
 * </ul>
 *
 * <p>只读信息（电量、连接态、固件、服务状态）不是能力值：它们由首页摘要卡与 About 信息行
 * 直接从 {@code DeviceSnapshot}/事件渲染，不占能力 id。</p>
 */
public enum DeviceCapabilityKind {
    TOGGLE,
    LEVEL,
    CHOICE,
    CURVE,
    ACTION,
    LIST,
    GATE
}
