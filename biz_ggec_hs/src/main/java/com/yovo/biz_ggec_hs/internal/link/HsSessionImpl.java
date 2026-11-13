package com.yovo.biz_ggec_hs.internal.link;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import android.os.Handler;
import android.os.Looper;

import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolInfo;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolPaired;
import com.yovo.biz_ggec_hs.internal.interact.HsModels;
import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;
import com.yovo.biz_ggec_hs.internal.center.port.HsConnectionEvents;
import com.yovo.biz_ggec_hs.internal.interact.HsCapabilityRouter;
import com.yovo.biz_ggec_hs.internal.interact.HsSnapshotAssembler;
import com.yovo.biz_ggec_hs.internal.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceDomainStore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话实现：把协议引擎的字节与事件整理成对外契约。
 *
 * <p>职责边界：能力→命令的映射在 {@link HsCapabilityRouter}，快照整理在
 * {@link HsSnapshotAssembler}，失败归一在 {@link HsFailureMapper}；本类只做编排——
 * 判定可写/已知、下发命令、按主线程派发结果与事件。</p>
 *
 * <p>就绪看门狗的计时窗口由引擎的连接事件界定：起表在 {@code onConnecting}
 * （尝试开始），撤表在 {@code onConnected} / {@code onDisconnected} / {@code onConnectFailed}
 * （尝试终态）。</p>
 */
public final class HsSessionImpl {
    private static final String TAG = "HsSessionImpl";

    /** 连接尝试从开始到链路就绪的上限，超时则断开并上报。 */
    static final long CONNECT_READY_TIMEOUT_MILLIS = 12_000L;

    private final HsProtocolEngine engine;
    private final HsClassicRadio classicRadio;
    private final DeviceDomainStore domain;
    private final HsCatalogImpl catalog;
    private final HsCapabilityRouter router = new HsCapabilityRouter();
    private final HsSnapshotAssembler assembler = new HsSnapshotAssembler(router);
    private final MainThread mainThread;
    private final HsConnectReadyTimeout readyTimeout;
    private final CopyOnWriteArrayList<HsControl.Listener> observers = new CopyOnWriteArrayList<>();
    private volatile HsSessionState state = HsSessionState.idle();
    private volatile HsControl.Dual dual;
    /** 当前连接目标机型声明的能力集合；机型未登记时为空集（写入/动作一律不支持）。 */
    private volatile Set<HsDevice.Capability> sessionCapabilities = Set.of();
    /** 当前连接的产品型号、广播地址和扫描名。决定协议分支，也是界面标题的唯一来源。 */
    private volatile String connectedModelId = "";
    private volatile HsDevice.Radios connectedRadios = HsDevice.Radios.none();
    private volatile List<HsControl.Recording> recordings = List.of();
    /** 本会话是否已补查过聚合状态：每条会话一次，失败不重试。 */
    private volatile boolean deviceStatusQueried;
    /** 当前连接代次。经典回调迟到时不得再去开 GATT。 */
    private final AtomicInteger connectGeneration = new AtomicInteger();
    private final ConnectionEvents connectionEvents = new ConnectionEvents();

    public HsSessionImpl(HsProtocolEngine engine, HsCatalogImpl catalog, HsClassicRadio classicRadio) {
        this(engine, catalog, new HandlerMainThread(new Handler(Looper.getMainLooper())), classicRadio);
    }

    /**
     * 包内装配入口：主线程投递与看门狗时序都由 {@code mainThread} 决定，
     * 用例注入可控实现来推进时间、观察事件顺序。
     */
    HsSessionImpl(HsProtocolEngine engine, HsCatalogImpl catalog, MainThread mainThread,
                  HsClassicRadio classicRadio) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.classicRadio = Objects.requireNonNull(classicRadio, "classicRadio");
        this.domain = engine.domain();
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.readyTimeout = new HsConnectReadyTimeout(
                mainThread, CONNECT_READY_TIMEOUT_MILLIS, engine::onConnectAttemptTimedOut);
        // 连接事件恒挂在本类上：看门狗属于这次连接尝试，不取决于此刻有没有观察者。
        engine.setConnectionEvents(connectionEvents);
        engine.setDeviceObserver(new DomainEvents());
        engine.setRemoteStreamStop(this::onRemoteStreamStop);
    }

    public void connect(String modelId, HsDevice.Radios radios) {
        if (radios == null || radios.bleAddress() == null || radios.bleAddress().isBlank()) {
            throw new IllegalArgumentException("连接目标缺少 BLE 地址");
        }
        if (radios.classicAddress() == null || radios.classicAddress().isBlank()) {
            throw new IllegalArgumentException("连接目标缺少经典蓝牙地址");
        }
        connectedModelId = Objects.requireNonNull(modelId, "modelId");
        connectedRadios = radios;
        sessionCapabilities = capabilitiesOf(connectedModelId);
        deviceStatusQueried = false;
        dual = null;
        recordings = List.of();
        readyTimeout.cancel();
        int current = connectGeneration.incrementAndGet();
        String bleAddress = radios.bleAddress().trim();
        classicRadio.cancel();
        classicRadio.open(radios.classicAddress().trim(), new HsClassicRadio.Callback() {
            @Override
            public void onReady() {
                if (current != connectGeneration.get()) {
                    return;
                }
                engine.connect(bleAddress);
            }

            @Override
            public void onFailed(HsControl.Failure failure) {
                if (current != connectGeneration.get()) {
                    return;
                }
                connectionEvents.failBeforeLink(failure);
            }

            @Override
            public void onLost() {
                if (current != connectGeneration.get()) {
                    return;
                }
                if (engine.isReady()) {
                    engine.disconnect();
                } else {
                    connectionEvents.failBeforeLink(HsControl.Failure.DEVICE_ERROR);
                }
            }
        });
    }

    public HsControl.Peer peer() {
        return new HsControl.Peer(connectedModelId, connectedRadios);
    }

    public HsControl.Dual dual() {
        return dual;
    }

    public List<HsControl.Recording> recordings() {
        return recordings;
    }

    public void disconnect() {
        connectGeneration.incrementAndGet();
        readyTimeout.cancel();
        classicRadio.cancel();
        engine.disconnect();
    }

    private void clearPeer() {
        connectedModelId = "";
        connectedRadios = HsDevice.Radios.none();
        dual = null;
        recordings = List.of();
        deviceStatusQueried = false;
    }

    public boolean isReady() {
        return engine.isReady();
    }

    public HsControl.State connectionState() {
        return state.connection();
    }

    public HsDevice.Snapshot snapshot() {
        return assembler.assemble(state, domain, dual);
    }

    public void observe(HsControl.Listener observer) {
        Objects.requireNonNull(observer, "observer");
        observers.addIfAbsent(observer);
        mainThread.post(() -> {
            HsSessionState current = state;
            dispatch(() -> observer.onConnectionChanged(current.connection(), current.failure()));
            dispatch(() -> observer.onSnapshotChanged(snapshot()));
        });
    }

    public void removeObserver(HsControl.Listener observer) {
        observers.remove(observer);
    }

    public HsControl.Outcome<HsControl.Value> read(HsDevice.Capability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("能力不能为空");
        }
        if (!isReady()) {
            return new HsControl.Outcome.Err<>(HsControl.Failure.NOT_READY);
        }
        if (!isSupported(capability)) {
            return new HsControl.Outcome.Err<>(HsControl.Failure.UNSUPPORTED_CAPABILITY);
        }
        HsControl.Value value = router.value(capability, domain, dual);
        if (value == null) {
            // 设备还没上报过该项：契约里没有「未知值」分类，用 NOT_READY 表达「尚不可读」。
            return new HsControl.Outcome.Err<>(HsControl.Failure.NOT_READY);
        }
        return new HsControl.Outcome.Ok<>(value);
    }

    public void write(HsDevice.Capability capability, HsControl.Value value, HsControl.Callback callback) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(callback, "callback");
        if (!isReady()) {
            postResult(callback, new HsControl.Outcome.Err<>(HsControl.Failure.NOT_READY));
            return;
        }
        if (!isSupported(capability) || !HsCapabilityRouter.supportsWrite(capability)) {
            postResult(callback, new HsControl.Outcome.Err<>(HsControl.Failure.UNSUPPORTED_CAPABILITY));
            return;
        }
        // 能力与值不匹配属于调用方编程错误：当场抛出，不静默降级成一条失败回执。
        Command command = router.write(connectedModelId, capability, value);
        submit(command, callback);
    }

    public void perform(HsControl.Action action, HsControl.Callback callback) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(callback, "callback");
        if (!isReady()) {
            postResult(callback, new HsControl.Outcome.Err<>(HsControl.Failure.NOT_READY));
            return;
        }
        if (!isSupported(router.capabilityOf(action))) {
            postResult(callback, new HsControl.Outcome.Err<>(HsControl.Failure.UNSUPPORTED_CAPABILITY));
            return;
        }
        // 动作参数不合法同样属于调用方编程错误：当场抛出。
        submit(router.commands(action), callback);
    }

    public void close() {
        connectGeneration.incrementAndGet();
        readyTimeout.cancel();
        observers.clear();
        classicRadio.release();
        engine.release();
    }

    /** 一次写入/动作可能下发一条或多条命令，全部完成才给出唯一结果。 */
    private void submit(List<Command> commands, HsControl.Callback callback) {
        if (commands.size() == 1) {
            submit(commands.get(0), callback);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(commands.size());
        AtomicReference<HsControl.Failure> firstFailure = new AtomicReference<>();
        HsControl.Callback aggregator = result -> {
            if (result instanceof HsControl.Outcome.Err<Void> failed) {
                firstFailure.compareAndSet(null, failed.failure());
            }
            if (remaining.decrementAndGet() == 0) {
                HsControl.Failure failure = firstFailure.get();
                callback.onResult(failure == null
                        ? new HsControl.Outcome.Ok<>(null)
                        : new HsControl.Outcome.Err<>(failure));
            }
        };
        for (Command command : commands) {
            submit(command, aggregator);
        }
    }

    private void submit(Command command, HsControl.Callback callback) {
        engine.submit(command, (submitted, completion) -> mainThread.post(() -> {
            applyCompletion(submitted, completion);
            callback.onResult(HsFailureMapper.writeResult(completion));
        }));
    }

    /**
     * 命令回包里除「成功/失败」以外的结果落地：双连接页与录音列表由事件端口给出。
     * 判定只看回包形状，不按命令类型分支。
     */
    private void applyCompletion(Command command, CommandCompletion completion) {
        Optional<HsControl.Event> event = router.deviceEvent(command, completion);
        if (event.isEmpty()) {
            return;
        }
        HsControl.Event applied = event.get();
        if (applied instanceof HsControl.Event.DualConnectionChanged changed) {
            dual = changed.state();
        } else if (applied instanceof HsControl.Event.RecordFilesChanged changed) {
            recordings = changed.files();
        }
        dispatchEvent(applied);
        dispatchSnapshot();
    }

    private void postResult(HsControl.Callback callback, HsControl.Outcome<Void> result) {
        mainThread.post(() -> callback.onResult(result));
    }

    /** 当前机型是否声明了该能力。目录未登记机型时集合为空，因此一律不支持。 */
    private boolean isSupported(HsDevice.Capability capability) {
        return capability != null && sessionCapabilities.contains(capability);
    }

    private Set<HsDevice.Capability> capabilitiesOf(String modelId) {
        return catalog.model(modelId)
                .map(HsDevice.Product::capabilities)
                .map(Set::copyOf)
                .orElseGet(Set::of);
    }

    /**
     * READY 后的会话自愈：补查一次首页聚合状态，把固件/颜色/电量/服务状态并入快照。
     *
     * <p>每条会话只发一次，失败不重试，也不影响 READY 与连接状态——固件继续是「未上报」。
     * 该命令在策略表里是 SINGLE_FLIGHT，已有在途聚合查询时复用，不重复下发。</p>
     */
    private void queryDeviceStatusOnce() {
        if (deviceStatusQueried) {
            return;
        }
        deviceStatusQueried = true;
        for (Command query : router.sessionBootstrapQueries(connectedModelId)) {
            engine.submit(query, null);
        }
    }

    private void dispatchEvent(HsControl.Event event) {
        for (HsControl.Listener observer : observers) {
            dispatch(() -> observer.onDeviceEvent(event));
        }
    }

    private void dispatchSnapshot() {
        HsDevice.Snapshot snapshot = snapshot();
        for (HsControl.Listener observer : observers) {
            dispatch(() -> observer.onSnapshotChanged(snapshot));
        }
    }

    private void onRemoteStreamStop() {
        dispatchEvent(new HsControl.Event.RemoteStreamStop());
    }

    private static void dispatch(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "会话观察者执行失败", e);
        }
    }

    /**
     * 引擎连接事件的唯一入口：看门狗按「尝试开始 / 尝试终态」起表与撤表，
     * 同一事件再迁到会话状态并派发给观察者。
     */
    private final class ConnectionEvents implements HsConnectionEvents {

        @Override
        public void onConnecting(String macAddress) {
            readyTimeout.arm();
            publish(HsSessionState.connecting());
        }

        @Override
        public void onConnected(String macAddress) {
            readyTimeout.cancel();
            publish(HsSessionState.ready());
            queryDeviceStatusOnce();
        }

        @Override
        public void onDisconnected(String reason) {
            readyTimeout.cancel();
            // 换目标时 connect() 已经写好新的双模身份，这里清掉会把新目标抹掉。
            if (!HsProtocolEngine.REASON_TARGET_SWITCHED.equals(reason)) {
                clearPeer();
            }
            publish(HsSessionState.disconnected(HsFailureMapper.ofDisconnectReason(reason)));
        }

        void failBeforeLink(HsControl.Failure failure) {
            readyTimeout.cancel();
            clearPeer();
            publish(HsSessionState.disconnected(failure));
        }

        @Override
        public void onConnectFailed(String reason) {
            readyTimeout.cancel();
            clearPeer();
            publish(HsSessionState.disconnected(HsFailureMapper.ofConnectFailure(reason)));
        }

        private void publish(HsSessionState next) {
            state = next;
            HsControl.Failure failure = next.failure();
            HsControl.State connection = next.connection();
            for (HsControl.Listener observer : observers) {
                mainThread.post(() -> {
                    dispatch(() -> observer.onConnectionChanged(connection, failure));
                    dispatch(() -> observer.onSnapshotChanged(snapshot()));
                });
            }
        }
    }

    /** 设备域事件到对外事件的翻译：会话层之外没有任何地方认识 {@link HsControl.Event}。 */
    private final class DomainEvents implements DeviceDomainStore.Observer {

        @Override
        public void onBatteryChanged(DeviceDomainStore.BatterySnapshot snapshot,
                                     int fields,
                                     int code) {
            dispatchSnapshot();
        }

        @Override
        public void onEqModeChanged(int mode) {
            dispatchSnapshot();
        }

        @Override
        public void onGameModeChanged(boolean enabled) {
            dispatchSnapshot();
        }

        @Override
        public void onLdacChanged(boolean enabled) {
            dispatchSnapshot();
        }

        @Override
        public void onFindAlertChanged(int status) {
            dispatchEvent(new HsControl.Event.FindAlertChanged(
                    Command.ResultCode.SUCCESS, status));
        }

        @Override
        public void onVoiceAssistantChanged(int code) {
            dispatchEvent(new HsControl.Event.VoiceAssistantChanged(
                    Command.ResultCode.SUCCESS, code));
        }

        @Override
        public void onBeginnerModeChanged(int earType, int actionType) {
            dispatchEvent(new HsControl.Event.BeginnerModeChanged(earType, actionType));
        }

        @Override
        public void onBeginnerModeEnabledChanged(boolean enabled) {
            dispatchSnapshot();
        }

        @Override
        public void onLeftAndRightEarExchangeChanged(int earType) {
            dispatchEvent(new HsControl.Event.EarExchangeChanged(earType));
        }

        @Override
        public void onServiceStatusChanged(Map<String, String> status) {
            dispatchEvent(new HsControl.Event.ServiceStatusChanged(status));
        }

        @Override
        public void onPairedDevicesChanged(List<ProtocolPaired> devices) {
            dispatchEvent(new HsControl.Event.PairedDevicesChanged(HsModels.paired(devices)));
        }

        @Override
        public void onDeviceInfoChanged(ProtocolInfo info) {
            HsDevice.Info mapped = HsModels.info(info);
            if (mapped != null) {
                dispatchEvent(new HsControl.Event.DeviceInfoChanged(mapped));
            }
            dispatchSnapshot();
        }
    }

    /**
     * 主线程投递与看门狗计时共用的唯一调度出口。生产实现是主 {@link Handler}。
     */
    interface MainThread extends HsConnectReadyTimeout.Scheduler {

        void post(Runnable task);
    }

    private static final class HandlerMainThread implements MainThread {
        private final Handler handler;

        private HandlerMainThread(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void post(Runnable task) {
            handler.post(task);
        }

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            handler.postDelayed(task, delayMillis);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            handler.removeCallbacks(task);
        }
    }
}
