package com.yovo.biz_ggec_hs.internal.protocol.inbound;

import com.yovo.biz_ggec_hs.internal.log.HsLog;
import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 域事件入口：写入 {@link DeviceDomainStore}，并向 listen* / 电池监听扇出。
 * 帧路径续命由 {@link InboundFrameDemux} 负责。
 */
public final class DeviceEventPipeline implements DeviceDomainStore.Observer {
    private static final String TAG = "DeviceEventPipeline";

    private final DeviceDomainStore domain;
    private final DeviceEventRouter router;

    private volatile HsDeviceObserver observer;

    public DeviceEventPipeline() {
        this(new DeviceDomainStore());
    }

    public DeviceEventPipeline(DeviceDomainStore domain) {
        if (domain == null) {
            throw new IllegalArgumentException("事件管道依赖不能为空");
        }
        this.domain = domain;
        this.router = new DeviceEventRouter(domain);
        this.domain.addObserver(this);
    }

    /**
     * 设备主动结束录音时的桥。协议层不认识音频实现，由上层注入。
     */
    public void setRemoteStreamStop(Runnable trigger) {
        router.setStreamStopTrigger(trigger);
    }

    public DeviceDomainStore.BatterySnapshot batterySnapshot() {
        return domain.battery();
    }

    /** 同包测试 / 会话门禁清理用。 */
    DeviceDomainStore domain() {
        return domain;
    }

    public boolean onEvent(long sessionId, String frame) {
        try {
            return router.route(frame);
        } catch (RuntimeException e) {
            HsLog.e(TAG, "事件通道处理失败 session=" + sessionId
                    + " frame=" + frame, e);
            return false;
        }
    }

    /**
     * 首页聚合投影（C7 / 0x98）。电量以本次命令解析结果为准，含 {@code --}。
     * 命令未带的字段保持 Store 现值；回写 Map，保证回调与 Store 一致。
     */
    public void applyAggregatedHomeStatus(Map<String, String> status) {
        if (status == null || status.isEmpty()) {
            return;
        }
        DeviceDomainStore.BatterySnapshot current = domain.battery();
        if (status.containsKey("80_1")
                || status.containsKey("80_2")
                || status.containsKey("80_3")) {
            String left = firstPresent(status.get("80_1"), current.getLeftLevel());
            String right = firstPresent(status.get("80_2"), current.getRightLevel());
            String batteryCase = firstPresent(status.get("80_3"), current.getCaseLevel());
            domain.updateBatteryLevels(
                    left, right, batteryCase, Command.ResultCode.SUCCESS);
            status.put("80_1", left);
            status.put("80_2", right);
            status.put("80_3", batteryCase);
        }

        String charging = status.get("95");
        if (charging != null) {
            try {
                domain.updateCaseChargingStatus(
                        Integer.parseInt(charging, 16), Command.ResultCode.SUCCESS);
            } catch (NumberFormatException e) {
                HsLog.w(TAG, "首页状态中的充电状态格式无效: " + charging);
            }
        } else {
            status.put("95", current.isCaseCharging() ? "01" : "00");
        }

        String eq = status.get("83");
        if (eq != null && eq.length() >= 2) {
            try {
                int mode = Integer.parseInt(eq.substring(0, 2), 16);
                if (mode >= 0 && mode <= 5) {
                    domain.setEqMode(mode);
                }
            } catch (NumberFormatException e) {
                HsLog.w(TAG, "首页状态中的 EQ 模式无效: " + eq);
            }
        }

        Map<String, String> serviceStatus = serviceStatusFromHome(status);
        if (serviceStatus != null) {
            domain.setServiceStatus(serviceStatus);
        }
    }

    public void setObserver(HsDeviceObserver observer) {
        this.observer = observer;
        if (observer == null) {
            return;
        }
        Integer mode = domain.getEqMode();
        if (mode != null) {
            dispatch(() -> observer.onEqMode(mode));
        }
        Map<String, String> status = domain.getServiceStatus();
        if (status != null) {
            dispatch(() -> observer.onServiceStatus(status));
        }
        Boolean gameMode = domain.getGameMode();
        if (gameMode != null) {
            dispatch(() -> observer.onGameMode(gameMode));
        }
        Integer findAlert = domain.getFindAlertStatus();
        if (findAlert != null) {
            dispatch(() -> observer.onFindAlert(Command.ResultCode.SUCCESS, findAlert));
        }
        Integer voice = domain.getVoiceAssistantCode();
        if (voice != null) {
            dispatch(() -> observer.onVoiceAssistant(Command.ResultCode.SUCCESS, voice));
        }
        DeviceDomainStore.BeginnerModeSnapshot beginner = domain.getBeginnerMode();
        if (beginner != null) {
            dispatch(() -> observer.onBeginnerMode(beginner.getEarType(), beginner.getActionType()));
        }
        Integer earExchange = domain.getLeftAndRightEarExchange();
        if (earExchange != null) {
            dispatch(() -> observer.onEarExchange(earExchange));
        }
        List<Map<String, String>> devices = domain.getPairedDevices();
        if (devices != null) {
            dispatch(() -> observer.onPairedDevices(devices));
        }
        dispatchBattery(
                DeviceDomainStore.BATTERY_LEFT
                        | DeviceDomainStore.BATTERY_RIGHT
                        | DeviceDomainStore.BATTERY_CASE
                        | DeviceDomainStore.BATTERY_CHARGING
                        | DeviceDomainStore.BATTERY_IN_CASE);
    }

    public void notifyFileExportSucceeded() {
        router.notifyFileExportSucceeded();
    }

    public void notifyFileExportFailed() {
        router.notifyFileExportFailed();
    }

    public void clearListeners() {
        observer = null;
    }

    public void resetState() {
        domain.reset();
    }

    public void resetStateKeepingBattery() {
        domain.resetKeepingBattery();
    }

    public void clearBattery() {
        domain.clearBattery();
    }

    @Override
    public void onBatteryChanged(DeviceDomainStore.BatterySnapshot snapshot,
                                 int fields,
                                 int code) {
        dispatchBattery(fields);
    }

    @Override
    public void onEqModeChanged(int mode) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onEqMode(mode));
        }
    }

    @Override
    public void onGameModeChanged(boolean enabled) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onGameMode(enabled));
        }
    }

    @Override
    public void onFindAlertChanged(int status) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onFindAlert(Command.ResultCode.SUCCESS, status));
        }
    }

    @Override
    public void onVoiceAssistantChanged(int status) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onVoiceAssistant(Command.ResultCode.SUCCESS, status));
        }
    }

    @Override
    public void onBeginnerModeChanged(int earType, int actionType) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onBeginnerMode(earType, actionType));
        }
    }

    @Override
    public void onLeftAndRightEarExchangeChanged(int earType) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onEarExchange(earType));
        }
    }

    @Override
    public void onServiceStatusChanged(Map<String, String> status) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onServiceStatus(status));
        }
    }

    @Override
    public void onPairedDevicesChanged(List<Map<String, String>> devices) {
        HsDeviceObserver current = observer;
        if (current != null) {
            dispatch(() -> current.onPairedDevices(devices));
        }
    }

    private void dispatchBattery(int fields) {
        HsDeviceObserver current = observer;
        if (current == null) {
            return;
        }
        boolean leftUpdated = (fields & DeviceDomainStore.BATTERY_LEFT) != 0;
        boolean rightUpdated = (fields & DeviceDomainStore.BATTERY_RIGHT) != 0;
        boolean caseUpdated = (fields & DeviceDomainStore.BATTERY_CASE) != 0;
        boolean ancillary =
                (fields & DeviceDomainStore.BATTERY_CHARGING) != 0
                        || (fields & DeviceDomainStore.BATTERY_IN_CASE) != 0;
        if (!leftUpdated && !rightUpdated && !caseUpdated && !ancillary) {
            return;
        }
        dispatch(() ->
                current.onBatteryStateChanged(leftUpdated, rightUpdated, caseUpdated));
    }

    private static void dispatch(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "域事件回调执行失败", e);
        }
    }

    private static String firstPresent(String commandValue, String current) {
        return commandValue != null ? commandValue : current;
    }

    private static Map<String, String> serviceStatusFromHome(Map<String, String> status) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            String value = status.get("B5_" + i);
            if (value == null) {
                continue;
            }
            mapped.put(String.format(Locale.ROOT, "%02d", i), value);
        }
        return mapped.isEmpty() ? null : mapped;
    }
}
