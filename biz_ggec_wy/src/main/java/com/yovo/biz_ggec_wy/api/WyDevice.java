package com.yovo.biz_ggec_wy.api;

import com.yovo.biz_ggec_wy.api.WyControl.State;
import com.yovo.biz_ggec_wy.api.WyControl.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WY 设备信息与支持功能。项目、形态、型号和能力只在这里定义。
 */
public final class WyDevice {

    private WyDevice() {
    }

    /** 项目。{@code projectId} 是稳定标识，不从型号字符串切分。 */
    public record Project(String projectId, String displayName) {
    }

    /** 设备形态。逐型号声明，不从项目推导。 */
    public enum Type {
        EARBUDS, SPEAKER, HEADBAND
    }

    /** 产品型号。{@code modelId} 与扫描结果同源，例如 {@code WY01}。 */
    public record Product(String modelId, String displayName, String projectId,
                          Type deviceType, Set<Capability> capabilities) {
    }

    /**
     * 经典蓝牙身份。
     * {@code classicAddress} 是 BD_ADDR，{@code classicName} 是经典蓝牙名称。
     */
    public record Radios(String classicAddress, String classicName) {
        public static Radios none() {
            return new Radios("", "");
        }
    }

    /** 能力 id。机型是否支持，看 {@link Product#capabilities()}。 */
    public enum Capability {
        CONNECTION, RECORD_STREAM, DEVICE_INFO, BATTERY
    }

    /** 设置分组。 */
    public enum Group {
        CONTROL, RECORD, ABOUT
    }

    /** 控件形态。{@code LEVEL} 必须带值域；{@code GATE} 只做能力门控。 */
    public enum Form {
        TOGGLE, LEVEL, CHOICE, ACTION, GATE
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
    public record Battery(int leftLevel, int rightLevel, int caseLevel, boolean caseCharging) {
        public static final int UNKNOWN_LEVEL = -1;

        public static Battery unknown() {
            return new Battery(UNKNOWN_LEVEL, UNKNOWN_LEVEL, UNKNOWN_LEVEL, false);
        }
    }

    /** 固件信息。未上报用 {@link #UNKNOWN_FIRMWARE}。 */
    public record Info(String firmware, String hardwareVersion) {
        public static final String UNKNOWN_FIRMWARE = "";
        public static final String UNKNOWN_HARDWARE = "";

        public static Info unknown() {
            return new Info(UNKNOWN_FIRMWARE, UNKNOWN_HARDWARE);
        }
    }

    /** 会话快照。未上报的能力不放占位值。 */
    public record Snapshot(State connection, Battery battery, Info deviceInfo,
                           Map<Capability, Value> values) {
    }
}
