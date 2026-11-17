package com.yovo.biz_ggec_hs.api;

import com.yovo.biz_ggec_hs.api.HsControl.State;
import com.yovo.biz_ggec_hs.api.HsControl.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 设备信息与支持功能。项目、形态、型号和能力只在这里定义。
 */
public final class HsDevice {

    private HsDevice() {
    }

    /** 项目。{@code projectId} 是稳定标识，不从型号字符串切分。 */
    public record Project(String projectId, String displayName) {
    }

    /** 设备形态。逐型号声明，不从项目推导。 */
    public enum Type {
        EARBUDS, SPEAKER, HEADBAND
    }

    /** 产品型号。{@code modelId} 与扫描结果同源，例如 {@code HS01}。 */
    public record Product(String modelId, String displayName, String projectId,
                          Type deviceType, Set<Capability> capabilities) {
    }

    /**
     * 双模身份。HS 同时有 LE 和经典蓝牙，四项各说各的。
     * 空串表示这一侧还没上报，不得用另一侧的值填上。
     *
     * <p>{@code bleAddress} 是扫描和 GATT 使用的 LE 地址。{@code bleName} 只来自广播本地名。
     * {@code classicAddress} 是广播里算出的经典 BD_ADDR：HS01 取服务 UUID 末 6 字节，
     * HS02 取厂商数据里的经典地址，两边算法不同，调用方不得再按 BLE 地址推算。
     * {@code classicName} 是经典蓝牙名称。</p>
     */
    public record Radios(String bleAddress, String bleName, String classicAddress, String classicName) {
        public static Radios none() {
            return new Radios("", "", "", "");
        }
    }

    /** 能力 id。机型是否支持，看 {@link Product#capabilities()}。 */
    public enum Capability {
        CONNECTION, DUAL_CONNECTION, DUAL_CONNECTION_DEVICES, BATTERY, SOUND_QUALITY, EQ_MODE,
        CUSTOM_EQ,
        LDAC, VOLUME_ADAPTIVE,
        TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP, RESET_CONTROL,
        WEAR_DETECT, VOICE_WAKEUP, GAME_MODE, FALL_ALERT, FIND_EAR, VOLUME_REMIND,
        DEVICE_INFO, RENAME, REBOOT, CLEAR_PAIRING,
        RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
        AI_ASSISTANT, SERVICE_STATUS, BEGINNER_MODE, INTERPRET, OTA, OFFLINE_LOG,
        CLASSIC_BT
    }

    /** 设置分组。 */
    public enum Group {
        MUSIC, CONTROL, FUNCTION, DUAL_CONNECTION, RECORD, ABOUT
    }

    /** 控件形态。{@code LEVEL} 必须带值域；{@code GATE} 只做能力门控。 */
    public enum Form {
        TOGGLE, LEVEL, CHOICE, CURVE, ACTION, LIST, GATE
    }

    public record Option(String optionId, String title) {
    }

    /**
     * 取值范围。{@code step} 恒为正；单位由能力自己定义（自定义 EQ 用协议增益单位，0x01 = 0.5 dB）。
     *
     * <p>{@code neutral} 是这条能力声明的中立档（自定义 EQ 即协议 {@code 0x0C} = 0 dB）：哪一档算
     * 「不增不减」只有描述符知道，调用方不得再从上下界推算，也不得拿 {@code min} 充当中立档。
     * 空＝这条能力没有声明中立档，调用方要回落到什么值必须写成自己的显式产品默认。</p>
     */
    public record Range(int min, int max, int step, OptionalInt neutral) {

        /** 没有中立档的值域：显式声明缺失，不是补一个默认值。 */
        public static Range of(int min, int max, int step) {
            return new Range(min, max, step, OptionalInt.empty());
        }
    }

    /**
     * 一条能力的呈现。
     *
     * <p>{@code kind == LEVEL}（等级）与 {@code kind == CURVE}（曲线）时 {@code range} 必有值；
     * {@code CURVE} 的 {@code options} 是曲线段表（自定义 EQ 即 10 个频段），段数等于选项数，
     * 界面不猜段数、不猜取值域。</p>
     */
    public record Feature(Capability capability, String title, Group group, Form kind,
                          List<Option> options, Optional<Range> range) {
    }

    /** 耳机电量。未知只用 {@link #UNKNOWN_LEVEL}。 */
    public record Battery(int leftLevel, int rightLevel, int caseLevel,
                          boolean caseCharging, int earInCaseStatus) {
        public static final int UNKNOWN_LEVEL = -1;
    }

    public enum Ear {
        UNKNOWN, NONE, LEFT, RIGHT, BOTH
    }

    /** 固件、颜色、在仓。未上报用 {@link #UNKNOWN_FIRMWARE}、{@link #UNKNOWN_EAR_COLOR}、{@link Ear#UNKNOWN}。 */
    public record Info(String firmware, int earColor, Ear earInCase) {
        public static final String UNKNOWN_FIRMWARE = "";
        public static final int UNKNOWN_EAR_COLOR = -1;
    }

    /** 会话快照。未上报的能力不放占位值。 */
    public record Snapshot(State connection, Battery battery, Info deviceInfo,
                           Map<Capability, Value> values) {
    }
}
