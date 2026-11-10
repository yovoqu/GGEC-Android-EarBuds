package com.yovo.biz_ggec_hs.internal.protocol.inbound;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        default void onFindAlertChanged(int status) {}

        default void onVoiceAssistantChanged(int code) {}

        default void onBeginnerModeChanged(int earType, int actionType) {}

        default void onLeftAndRightEarExchangeChanged(int earType) {}

        default void onServiceStatusChanged(Map<String, String> status) {}

        default void onPairedDevicesChanged(List<Map<String, String>> devices) {}
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
    private Integer findAlertStatus;
    private Integer voiceAssistantCode;
    private BeginnerModeSnapshot beginnerMode;
    private Integer leftAndRightEarExchange;
    private Map<String, String> serviceStatus;
    private List<Map<String, String>> pairedDevices;

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

    public List<Map<String, String>> getPairedDevices() {
        synchronized (this) {
            if (pairedDevices == null) {
                return null;
            }
            List<Map<String, String>> copy = new ArrayList<>(pairedDevices.size());
            for (Map<String, String> item : pairedDevices) {
                copy.add(new LinkedHashMap<>(item));
            }
            return copy;
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

    void setPairedDevices(List<Map<String, String>> devices) {
        List<Map<String, String>> copy;
        synchronized (this) {
            if (devices == null) {
                pairedDevices = null;
                copy = null;
            } else {
                List<Map<String, String>> next = new ArrayList<>(devices.size());
                for (Map<String, String> item : devices) {
                    next.add(item == null
                            ? Collections.emptyMap()
                            : new LinkedHashMap<>(item));
                }
                pairedDevices = next;
                copy = getPairedDevices();
            }
        }
        if (copy == null) {
            return;
        }
        for (Observer observer : observers) {
            List<Map<String, String>> delivered = copy;
            safe(() -> observer.onPairedDevicesChanged(delivered));
        }
    }

    /**
     * 清会话态（含电量）。不回调，避免拆链瞬间用空快照冲一次监听。
     * 下一会话只接受设备新上报 / 新查询。
     */
    void reset() {
        reset(false);
    }

    /**
     * 清会话态，但留下电量快照。被动断开进入自动重连时使用。
     * 保留不是冻结：随后的主动上报仍覆盖对应耳并回调，未出现在本包里的一侧才保持上次值。
     */
    void resetKeepingBattery() {
        reset(true);
    }

    /** 只清电量，不回调。自动重连放弃时使用。 */
    void clearBattery() {
        synchronized (this) {
            battery = emptyBattery();
        }
    }

    private void reset(boolean keepBattery) {
        synchronized (this) {
            if (!keepBattery) {
                battery = emptyBattery();
            }
            eqMode = null;
            gameMode = null;
            findAlertStatus = null;
            voiceAssistantCode = null;
            beginnerMode = null;
            leftAndRightEarExchange = null;
            serviceStatus = null;
            pairedDevices = null;
        }
    }

    private void notifyBattery(BatterySnapshot snapshot, int fields, int code) {
        for (Observer observer : observers) {
            safe(() -> observer.onBatteryChanged(snapshot, fields, code));
        }
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

    private static String normalizeLevel(String level) {
        if (level == null || "--".equals(level)) {
            return "--";
        }
        try {
            int value = Integer.parseInt(level);
            return value >= 1 && value <= 100 ? String.valueOf(value) : "--";
        } catch (NumberFormatException ignored) {
            return "--";
        }
    }
}
