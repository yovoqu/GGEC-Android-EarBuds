package com.ggec.earbuds.ui.device;

/**
 * 设备能力 id 的常量池：界面里唯一允许出现能力 id 字面量的地方。
 *
 * <p>能力 id 来自 biz 目录（{@code HsDevice.Capability.name()}，经 {@code DeviceCapabilityView.id()} 进入
 * APP），界面只在自己的呈现词汇里引用它——用于把「需要专门组件的行」路由到对应视图
 * （自定义 EQ 曲线、录音列表、双连接设备列表、查找耳机、改名、重启、清除配对、重置手势等）。
 * 除本类之外，界面不得再用别处的字符串字面量判断能力。</p>
 *
 * <p>{@link #LDAC} 与 {@link #VOLUME_ADAPTIVE} 是通用 TOGGLE 能力，没有专门组件：它们只作为
 * 「这条能力必须按通用开关行渲染」的断言锚点在测试里出现，界面代码不按 id 分支。</p>
 */
public final class DeviceCapabilityIds {

    public static final String CUSTOM_EQ = "CUSTOM_EQ";
    public static final String LDAC = "LDAC";
    public static final String VOLUME_ADAPTIVE = "VOLUME_ADAPTIVE";

    public static final String FIND_EAR = "FIND_EAR";
    public static final String RENAME = "RENAME";
    public static final String REBOOT = "REBOOT";
    public static final String CLEAR_PAIRING = "CLEAR_PAIRING";
    public static final String RESET_CONTROL = "RESET_CONTROL";

    public static final String RECORD_LOCAL = "RECORD_LOCAL";
    public static final String RECORD_STREAM = "RECORD_STREAM";
    public static final String RECORD_LIST = "RECORD_LIST";
    public static final String RECORD_TRANSFER = "RECORD_TRANSFER";

    public static final String DUAL_CONNECTION = "DUAL_CONNECTION";
    public static final String DUAL_CONNECTION_DEVICES = "DUAL_CONNECTION_DEVICES";

    private DeviceCapabilityIds() {
    }
}
