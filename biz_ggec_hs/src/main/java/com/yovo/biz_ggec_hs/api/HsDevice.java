package com.yovo.biz_ggec_hs.api;

import com.yovo.biz_ggec_hs.api.HsControl.State;
import com.yovo.biz_ggec_hs.api.HsControl.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public record Range(int min, int max, int step) {
    }

    /** 一条能力的呈现。{@code form == LEVEL} 时 {@code range} 必有值。 */
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
