package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;


import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolInfo;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolPaired;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 域事件入口：写入 {@link DeviceDomainStore}，并向同包观察者扇出。
 * 帧路径续命由 {@link InboundFrameDemux} 负责。
 *
 * <p>观察者端口就是 {@link DeviceDomainStore.Observer}：本类挂在域存储上做落地，
 * 同时把它收到的事件转发给挂在会话上的那一个观察者。除落地与转发外不写业务判断。</p>
 */
public final class DeviceEventPipeline implements DeviceDomainStore.Observer {
    private static final String TAG = "DeviceEventPipeline";

    private final DeviceDomainStore domain;
    private final DeviceEventRouter router;

    private volatile DeviceDomainStore.Observer observer;

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

    /** 设备域状态的唯一读取口。 */
    public DeviceDomainStore domain() {
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

        // 首页聚合是固件的唯一来源；解析层已把 0x94 解成 UTF-8 文本，这里不再丢弃。
        String firmware = status.get("94");
        if (firmware != null && !firmware.isEmpty()) {
            domain.setFirmware(firmware);
        }

        Map<String, String> serviceStatus = serviceStatusFromHome(status);
        if (serviceStatus != null) {
            domain.setServiceStatus(serviceStatus);
        }
    }

    /**
     * 耳机颜色是独立命令（{@code 99EC91}）的结果，不进首页聚合。
     */
    public void applyEarColor(int earColor) {
        domain.setEarColor(earColor);
    }

    /**
     * LDAC 开关状态只由查询命令（{@code 99ECCF}）回包给出，设备不会主动上报。
     */
    public void applyLdacEnabled(boolean enabled) {
        domain.setLdacEnabled(enabled);
    }

    /**
     * 新手模式开关只在进入/退出命令成功后成立（设备应答即事实）。
     */
    public void applyBeginnerModeEnabled(boolean enabled) {
        domain.setBeginnerModeEnabled(enabled);
    }

    /**
     * 会话边界清空本会话域状态：连接发起处建立新会话，断连 / 链路失效处结束旧会话。
     *
     * <p>域状态只属于一次设备会话。上一台设备的固件 / 颜色 / 在仓状态 / 电量若留到下一次
     * {@link #setObserver}，新会话会把别人的值当成当前设备回放。两个调用点语义不同——
     * 起点保证新连接从「未知」开始，终点保证断连后读到未知而不是上一台设备的值——
     * 但清理动作只有这一份。不投递回调：屏蔽在建链 / 拆链瞬间用空快照冲一次监听。</p>
     */
    public void clearSessionState() {
        domain.reset();
    }

    /**
     * 挂载观察者并回放域里已有的值：固件 / 颜色 / 在仓状态 / 电量 / EQ / 游戏模式 / 服务状态 /
     * 已配对设备。回放走的是同一套端口方法，没有第二条通路。
     */
    public void setObserver(DeviceDomainStore.Observer observer) {
        this.observer = observer;
        if (observer == null) {
            return;
        }
        Integer mode = domain.getEqMode();
        if (mode != null) {
            dispatch(() -> observer.onEqModeChanged(mode));
        }
        Map<String, String> status = domain.getServiceStatus();
        if (status != null) {
            dispatch(() -> observer.onServiceStatusChanged(status));
        }
        Boolean gameMode = domain.getGameMode();
        if (gameMode != null) {
            dispatch(() -> observer.onGameModeChanged(gameMode));
        }
        Boolean ldac = domain.getLdacEnabled();
        if (ldac != null) {
            dispatch(() -> observer.onLdacChanged(ldac));
        }
        Integer findAlert = domain.getFindAlertStatus();
        if (findAlert != null) {
            dispatch(() -> observer.onFindAlertChanged(findAlert));
        }
        Integer voice = domain.getVoiceAssistantCode();
        if (voice != null) {
            dispatch(() -> observer.onVoiceAssistantChanged(voice));
        }
        DeviceDomainStore.BeginnerModeSnapshot beginner = domain.getBeginnerMode();
        if (beginner != null) {
            dispatch(() -> observer.onBeginnerModeChanged(
                    beginner.getEarType(), beginner.getActionType()));
        }
        Boolean beginnerModeEnabled = domain.getBeginnerModeEnabled();
        if (beginnerModeEnabled != null) {
            dispatch(() -> observer.onBeginnerModeEnabledChanged(beginnerModeEnabled));
        }
        Integer earExchange = domain.getLeftAndRightEarExchange();
        if (earExchange != null) {
            dispatch(() -> observer.onLeftAndRightEarExchangeChanged(earExchange));
        }
        List<ProtocolPaired> devices = domain.getPairedDevices();
        if (devices != null) {
            dispatch(() -> observer.onPairedDevicesChanged(devices));
        }
        ProtocolInfo deviceInfo = domain.protocolInfo();
        if (deviceInfo != null) {
            dispatch(() -> observer.onDeviceInfoChanged(deviceInfo));
        }
        DeviceDomainStore.BatterySnapshot battery = domain.battery();
        dispatch(() -> observer.onBatteryChanged(
                battery,
                DeviceDomainStore.BATTERY_LEFT
                        | DeviceDomainStore.BATTERY_RIGHT
                        | DeviceDomainStore.BATTERY_CASE
                        | DeviceDomainStore.BATTERY_CHARGING
                        | DeviceDomainStore.BATTERY_IN_CASE,
                Command.ResultCode.SUCCESS));
    }

    public void notifyFileExportSucceeded() {
        router.notifyFileExportSucceeded();
    }

    public void notifyFileExportFailed() {
        router.notifyFileExportFailed();
    }

    @Override
    public void onBatteryChanged(DeviceDomainStore.BatterySnapshot snapshot,
                                 int fields,
                                 int code) {
        // 在仓状态同时是设备信息的一项：落到域里随 onDeviceInfoChanged 出业务层，
        // 上层才能在不读电量快照的前提下拿到它。
        if ((fields & DeviceDomainStore.BATTERY_IN_CASE) != 0) {
            domain.setEarInCaseStatus(snapshot.getEarInCaseStatus());
        }
        forward(current -> current.onBatteryChanged(snapshot, fields, code));
    }

    @Override
    public void onEqModeChanged(int mode) {
        forward(current -> current.onEqModeChanged(mode));
    }

    @Override
    public void onGameModeChanged(boolean enabled) {
        forward(current -> current.onGameModeChanged(enabled));
    }

    @Override
    public void onLdacChanged(boolean enabled) {
        forward(current -> current.onLdacChanged(enabled));
    }

    @Override
    public void onFindAlertChanged(int status) {
        forward(current -> current.onFindAlertChanged(status));
    }

    @Override
    public void onVoiceAssistantChanged(int code) {
        forward(current -> current.onVoiceAssistantChanged(code));
    }

    @Override
    public void onBeginnerModeChanged(int earType, int actionType) {
        forward(current -> current.onBeginnerModeChanged(earType, actionType));
    }

    @Override
    public void onBeginnerModeEnabledChanged(boolean enabled) {
        forward(current -> current.onBeginnerModeEnabledChanged(enabled));
    }

    @Override
    public void onLeftAndRightEarExchangeChanged(int earType) {
        forward(current -> current.onLeftAndRightEarExchangeChanged(earType));
    }

    @Override
    public void onServiceStatusChanged(Map<String, String> status) {
        forward(current -> current.onServiceStatusChanged(status));
    }

    @Override
    public void onPairedDevicesChanged(List<ProtocolPaired> devices) {
        forward(current -> current.onPairedDevicesChanged(devices));
    }

    @Override
    public void onDeviceInfoChanged(ProtocolInfo info) {
        forward(current -> current.onDeviceInfoChanged(info));
    }

    private void forward(java.util.function.Consumer<DeviceDomainStore.Observer> action) {
        DeviceDomainStore.Observer current = observer;
        if (current == null) {
            return;
        }
        dispatch(() -> action.accept(current));
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
