package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;



import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 设备域状态唯一内存源。
 * 写入仅限同包 {@link DeviceEventRouter} / {@link DeviceEventPipeline}。
 */
public final class DeviceDomainStore {
    private static final String TAG = "DeviceDomainStore";

    public static final int BATTERY_LEFT = 1;
    public static final int BATTERY_RIGHT = 1 << 1;
    public static final int BATTERY_CASE = 1 << 2;
    public static final int BATTERY_CHARGING = 1 << 3;
    public static final int BATTERY_IN_CASE = 1 << 4;

    public interface Observer {
        default void onBatteryChanged(BatterySnapshot snapshot, int fields, int code) {}

        default void onEqModeChanged(int mode) {}

        default void onGameModeChanged(boolean enabled) {}
        default void onLdacChanged(boolean enabled) {}

        default void onBeginnerModeEnabledChanged(boolean enabled) {}
        default void onFindAlertChanged(int status) {}

        default void onVoiceAssistantChanged(int code) {}

        default void onBeginnerModeChanged(int earType, int actionType) {}

        default void onLeftAndRightEarExchangeChanged(int earType) {}

        default void onServiceStatusChanged(Map<String, String> status) {}

        default void onPairedDevicesChanged(List<HsControl.Paired> devices) {}

        default void onDeviceInfoChanged(HsDevice.Info info) {}
    }

    public static final class BatterySnapshot {
        private final String leftLevel;
        private final String rightLevel;
        private final String caseLevel;
        private final int caseChargingStatus;
        private final int earInCaseStatus;

        BatterySnapshot(String leftLevel,
                        String rightLevel,
                        String caseLevel,
                        int caseChargingStatus,
                        int earInCaseStatus) {
            this.leftLevel = leftLevel;
            this.rightLevel = rightLevel;
            this.caseLevel = caseLevel;
            this.caseChargingStatus = caseChargingStatus;
            this.earInCaseStatus = earInCaseStatus;
        }

        public String getLeftLevel() {
            return leftLevel;
        }

        public String getRightLevel() {
            return rightLevel;
        }

        public String getCaseLevel() {
            return caseLevel;
        }

        public int getCaseChargingStatus() {
            return caseChargingStatus;
        }

        public boolean isCaseCharging() {
            return caseChargingStatus == 1;
        }

        public int getEarInCaseStatus() {
            return earInCaseStatus;
        }

        public boolean isLeftEarInCase() {
            return earInCaseStatus == 1 || earInCaseStatus == 3;
        }

        public boolean isRightEarInCase() {
            return earInCaseStatus == 2 || earInCaseStatus == 3;
        }
    }

    public static final class BeginnerModeSnapshot {
        private final int earType;
        private final int actionType;

        public BeginnerModeSnapshot(int earType, int actionType) {
            this.earType = earType;
            this.actionType = actionType;
        }

        public int getEarType() {
            return earType;
        }

        public int getActionType() {
            return actionType;
        }
    }

    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private volatile BatterySnapshot battery = emptyBattery();
    private Integer eqMode;
    private Boolean gameMode;
    private Boolean ldacEnabled;
    private Integer findAlertStatus;
    private Integer voiceAssistantCode;
    private BeginnerModeSnapshot beginnerMode;
    /**
     * 新手模式开关。设备只在进入/退出命令应答里给出该事实（{@code 99ECCC}/{@code 99ECCD}），
     * 主动上报（{@code 99ECD2}）给的是配置（耳侧 + 动作），两者不同源、互不替代。
     */
    private Boolean beginnerModeEnabled;
    private Integer leftAndRightEarExchange;
    private Map<String, String> serviceStatus;
    private List<HsControl.Paired> pairedDevices;
    private String firmware;
    private Integer earColor;
    /**
     * 设备信息投影里的在仓状态。与 {@link BatterySnapshot#getEarInCaseStatus()} 不同源不同生存期：
     * 电量快照的起点是「空电池」（状态 0，不是未知标记），而设备信息里的未知必须用
     * {@link HsDevice.Ear#UNKNOWN} 表达，故由 {@link DeviceEventPipeline} 按
     * {@code BATTERY_IN_CASE} 事件单独写入。
     */
    private Integer earInCaseStatus;

    public void addObserver(Observer observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public BatterySnapshot battery() {
        return battery;
    }

    public Integer getEqMode() {
        synchronized (this) {
            return eqMode;
        }
    }

    public Boolean getGameMode() {
        synchronized (this) {
            return gameMode;
        }
    }

    public Boolean getLdacEnabled() {
        synchronized (this) {
            return ldacEnabled;
        }
    }

    public Integer getFindAlertStatus() {
        synchronized (this) {
            return findAlertStatus;
        }
    }

    public Integer getVoiceAssistantCode() {
        synchronized (this) {
            return voiceAssistantCode;
        }
    }

    public BeginnerModeSnapshot getBeginnerMode() {
        synchronized (this) {
            return beginnerMode;
        }
    }

    public Boolean getBeginnerModeEnabled() {
        synchronized (this) {
            return beginnerModeEnabled;
        }
    }

    public Integer getLeftAndRightEarExchange() {
        synchronized (this) {
            return leftAndRightEarExchange;
        }
    }

    public Map<String, String> getServiceStatus() {
        synchronized (this) {
            return serviceStatus == null ? null : new LinkedHashMap<>(serviceStatus);
        }
    }

    public List<HsControl.Paired> getPairedDevices() {
        synchronized (this) {
            return pairedDevices == null ? null : List.copyOf(pairedDevices);
        }
    }

    /**
     * 当前已知的设备信息；固件、颜色与在仓状态都未上报时返回 null（无信息，而不是「全未知」快照）。
     */
    public HsDevice.Info getDeviceInfo() {
        synchronized (this) {
            return deviceInfoLocked();
        }
    }

    void updateBatteryLevels(String left, String right, String batteryCase, int code) {
        BatterySnapshot next;
        synchronized (this) {
            BatterySnapshot current = battery;
            next = new BatterySnapshot(
                    normalizeLevel(left),
                    normalizeLevel(right),
                    normalizeLevel(batteryCase),
                    current.caseChargingStatus,
                    current.earInCaseStatus);
            battery = next;
        }
        notifyBattery(next, BATTERY_LEFT | BATTERY_RIGHT | BATTERY_CASE, code);
    }

    void updateBatteryLevel(int batteryId, String level, int code) {
        if (batteryId < 1 || batteryId > 3) {
            HsLog.w(TAG, "忽略无效的电池编号: " + batteryId);
            return;
        }
        BatterySnapshot next;
        int fields;
        synchronized (this) {
            BatterySnapshot current = battery;
            String normalized = normalizeLevel(level);
            String left = batteryId == 1 ? normalized : current.leftLevel;
            String right = batteryId == 2 ? normalized : current.rightLevel;
            String batteryCase = batteryId == 3 ? normalized : current.caseLevel;
            next = new BatterySnapshot(
                    left, right, batteryCase,
                    current.caseChargingStatus,
                    current.earInCaseStatus);
            battery = next;
            fields = batteryId == 1 ? BATTERY_LEFT
                    : batteryId == 2 ? BATTERY_RIGHT
                    : BATTERY_CASE;
        }
        notifyBattery(next, fields, code);
    }

    void updateCaseChargingStatus(int status, int code) {
        if (status != 0 && status != 1) {
            HsLog.w(TAG, "忽略无效的充电盒充电状态: " + status);
            return;
        }
        BatterySnapshot next;
        synchronized (this) {
            BatterySnapshot current = battery;
            next = new BatterySnapshot(
                    current.leftLevel,
                    current.rightLevel,
                    current.caseLevel,
                    status,
                    current.earInCaseStatus);
            battery = next;
        }
        notifyBattery(next, BATTERY_CHARGING, code);
    }

    void updateEarInCaseStatus(int status, int code) {
        if (status < 0 || status > 3) {
            HsLog.w(TAG, "忽略无效的耳机在仓状态: " + status);
            return;
        }
        BatterySnapshot next;
        synchronized (this) {
            BatterySnapshot current = battery;
            next = new BatterySnapshot(
                    current.leftLevel,
                    current.rightLevel,
                    current.caseLevel,
                    current.caseChargingStatus,
                    status);
            battery = next;
        }
        notifyBattery(next, BATTERY_IN_CASE, code);
    }

    void setEqMode(int mode) {
        synchronized (this) {
            eqMode = mode;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onEqModeChanged(mode));
        }
    }

    void setGameMode(boolean enabled) {
        synchronized (this) {
            gameMode = enabled;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onGameModeChanged(enabled));
        }
    }

    void setLdacEnabled(boolean enabled) {
        synchronized (this) {
            ldacEnabled = enabled;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onLdacChanged(enabled));
        }
    }

    void setFindAlertStatus(int status) {
        synchronized (this) {
            findAlertStatus = status;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onFindAlertChanged(status));
        }
    }

    void setVoiceAssistantCode(int code) {
        synchronized (this) {
            voiceAssistantCode = code;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onVoiceAssistantChanged(code));
        }
    }

    void setBeginnerMode(int earType, int actionType) {
        BeginnerModeSnapshot snapshot = new BeginnerModeSnapshot(earType, actionType);
        synchronized (this) {
            beginnerMode = snapshot;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onBeginnerModeChanged(earType, actionType));
        }
    }

    void setBeginnerModeEnabled(boolean enabled) {
        synchronized (this) {
            beginnerModeEnabled = enabled;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onBeginnerModeEnabledChanged(enabled));
        }
    }

    void setLeftAndRightEarExchange(int earType) {
        synchronized (this) {
            leftAndRightEarExchange = earType;
        }
        for (Observer observer : observers) {
            safe(() -> observer.onLeftAndRightEarExchangeChanged(earType));
        }
    }

    void setServiceStatus(Map<String, String> status) {
        Map<String, String> copy;
        synchronized (this) {
            if (status == null || status.isEmpty()) {
                serviceStatus = null;
                copy = null;
            } else {
                serviceStatus = new LinkedHashMap<>(status);
                copy = new LinkedHashMap<>(status);
            }
        }
        if (copy == null) {
            return;
        }
        for (Observer observer : observers) {
            Map<String, String> delivered = copy;
            safe(() -> observer.onServiceStatusChanged(delivered));
        }
    }

    void setFirmware(String value) {
        String normalized = value == null || value.isEmpty() ? null : value;
        HsDevice.Info next;
        synchronized (this) {
            if (Objects.equals(firmware, normalized)) {
                return;
            }
            firmware = normalized;
            next = deviceInfoLocked();
        }
        notifyDeviceInfo(next);
    }

    void setEarColor(int value) {
        if (value < 0) {
            HsLog.w(TAG, "忽略无效的耳机颜色: " + value);
            return;
        }
        HsDevice.Info next;
        synchronized (this) {
            if (earColor != null && earColor == value) {
                return;
            }
            earColor = value;
            next = deviceInfoLocked();
        }
        notifyDeviceInfo(next);
    }

    void setEarInCaseStatus(int status) {
        HsDevice.Info next;
        synchronized (this) {
            if (earInCaseStatus != null && earInCaseStatus == status) {
                return;
            }
            earInCaseStatus = status;
            next = deviceInfoLocked();
        }
        notifyDeviceInfo(next);
    }

    void setPairedDevices(List<HsControl.Paired> devices) {
        List<HsControl.Paired> copy;
        synchronized (this) {
            if (devices == null) {
                pairedDevices = null;
                copy = null;
            } else {
                pairedDevices = List.copyOf(devices);
                copy = pairedDevices;
            }
        }
        if (copy == null) {
            return;
        }
        for (Observer observer : observers) {
            List<HsControl.Paired> delivered = copy;
            safe(() -> observer.onPairedDevicesChanged(delivered));
        }
    }

    /**
     * 清空整个会话态，含电量。不回调，避免拆链瞬间用空快照冲一次监听。
     *
     * <p>域状态只属于一次设备会话，因此由 {@link DeviceEventPipeline#clearSessionState()} 在
     * 会话起点与终点各调用一次：起点保证新连接从「未知」开始，终点保证断连后读到未知而不是
     * 上一台设备的值。下一会话只接受设备新上报 / 新查询。</p>
     */
    void reset() {
        synchronized (this) {
            battery = emptyBattery();
            eqMode = null;
            gameMode = null;
            ldacEnabled = null;
            findAlertStatus = null;
            voiceAssistantCode = null;
            beginnerMode = null;
            beginnerModeEnabled = null;
            leftAndRightEarExchange = null;
            serviceStatus = null;
            pairedDevices = null;
            firmware = null;
            earColor = null;
            earInCaseStatus = null;
        }
    }

    private void notifyBattery(BatterySnapshot snapshot, int fields, int code) {
        for (Observer observer : observers) {
            safe(() -> observer.onBatteryChanged(snapshot, fields, code));
        }
    }

    private void notifyDeviceInfo(HsDevice.Info info) {
        for (Observer observer : observers) {
            safe(() -> observer.onDeviceInfoChanged(info));
        }
    }

    /** 调用方须持锁。三项都未知时返回 null。 */
    private HsDevice.Info deviceInfoLocked() {
        if (firmware == null && earColor == null && earInCaseStatus == null) {
            return null;
        }
        return new HsDevice.Info(
                firmware == null ? HsDevice.Info.UNKNOWN_FIRMWARE : firmware,
                earColor == null ? HsDevice.Info.UNKNOWN_EAR_COLOR : earColor,
                earInCase(earInCaseStatus));
    }

    /** 协议在仓字节（0 都不在仓 / 1 左耳 / 2 右耳 / 3 双耳）到对外枚举的唯一转换点。 */
    private static HsDevice.Ear earInCase(Integer status) {
        if (status == null) {
            return HsDevice.Ear.UNKNOWN;
        }
        return switch (status) {
            case 0 -> HsDevice.Ear.NONE;
            case 1 -> HsDevice.Ear.LEFT;
            case 2 -> HsDevice.Ear.RIGHT;
            case 3 -> HsDevice.Ear.BOTH;
            default -> throw new IllegalArgumentException("耳机在仓状态超出协议范围: " + status);
        };
    }

    private static void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "域状态观察者执行失败", e);
        }
    }

    private static BatterySnapshot emptyBattery() {
        return new BatterySnapshot("--", "--", "--", 0, 0);
    }

    /**
     * 设备上报的电平文本归一。协议电平只有两种合法形态：{@code 1..100} 的十进制，或未知标记
     * {@code --}（与 {@code HsDevice.Battery.UNKNOWN_LEVEL} 对应）。
     *
     * <p>入站帧是外部输入，设备可能发不规范文本，所以这里容错为「未知」；但容错必须可观测：
     * 每一例非协议文本都记录警告，不静默丢弃。编程侧的非法值（构造 {@code HsDevice.Battery}）走严格校验
     * 并抛错，两种处理不混。</p>
     */
    private static String normalizeLevel(String level) {
        if (level == null) {
            HsLog.w(TAG, "电量电平缺失，按未知处理");
            return "--";
        }
        String trimmed = level.trim();
        if ("--".equals(trimmed)) {
            return "--";
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value >= 1 && value <= 100) {
                return String.valueOf(value);
            }
            HsLog.w(TAG, "电量电平超出 1..100，按未知处理: " + level);
            return "--";
        } catch (NumberFormatException notDecimal) {
            HsLog.w(TAG, "电量电平不是十进制文本，按未知处理: " + level);
            return "--";
        }
    }
}
