package com.yovo.biz_ggec_hs.internal.session;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.internal.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertLeftCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertRightCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertStopCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.GameModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.WearDetectionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.LDACCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.SoundQualityModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.VolumeAdaptiveCommand;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsResultCallback;

import java.util.List;
import java.util.Map;

/**
 * 公开会话的实现。只组装协议命令，并把回调切回主线程。
 *
 * <p>就绪看门狗（{@link ConnectReadyTimeout}）的计时窗口由引擎的连接事件界定，不由
 * {@link #connect(String)} 的调用时刻界定：起表在引擎派发 {@code onConnecting}（尝试开始），
 * 撤表在 {@code onConnected} / {@code onDisconnected} / {@code onConnectFailed}（尝试终态）。
 * 理由见 {@link #connect(String)}。</p>
 */
public final class YoHsClientImpl implements YoHsClient {
    /** 连接尝试从开始到链路就绪的上限，超时则断开并上报 ready-timeout。 */
    private static final long CONNECT_READY_TIMEOUT_MILLIS = 12_000L;

    private final HsProtocolEngine engine;
    private final MainThread mainThread;
    private final ConnectReadyTimeout readyTimeout;
    private final ConnectionEvents connectionEvents = new ConnectionEvents();

    public YoHsClientImpl(HsProtocolEngine engine) {
        this(engine, new HandlerMainThread(new Handler(Looper.getMainLooper())));
    }

    /**
     * 包内唯一装配入口：主线程投递与看门狗时序都由 {@code mainThread} 决定，
     * 用例注入可控实现来推进时间、观察事件顺序。
     */
    YoHsClientImpl(HsProtocolEngine engine, MainThread mainThread) {
        this.engine = engine;
        this.mainThread = mainThread;
        this.readyTimeout = new ConnectReadyTimeout(
                mainThread,
                CONNECT_READY_TIMEOUT_MILLIS,
                this::onConnectReadyTimeout);
        // 连接事件恒挂在本类上：看门狗属于这次连接尝试，不取决于上层此刻有没有在听事件。
        engine.setConnectionObserver(connectionEvents);
    }

    /**
     * 发起连接。看门狗不在调用线程起表，原因在时序上：
     *
     * <p>换目标连接而不先 {@code disconnect()} 时，引擎线程会先跑
     * {@code startConnectAttempt → endSessionAndNotify("连接目标已切换")}，由此发出<b>旧会话</b>的
     * {@code onDisconnected}；随后才派发<b>本次尝试</b>的 {@code onConnecting}。若在调用线程
     * {@code engine.connect} 之后 arm，则与旧会话 {@code onDisconnected} 的 cancel 之间没有
     * happens-before，实际顺序是「arm 被随后到达的 cancel 撤掉」——本次尝试就不再有任何终态。</p>
     *
     * <p>把起表放到引擎派发 {@code onConnecting} 处，顺序天然是「旧会话撤表 → 本次尝试起表」，
     * 且窗口的起点就是引擎定义的尝试起点。这里仍先撤一次表：新的连接请求立即作废上一窗口，
     * 不让上一次尝试的剩余时间算到本次头上。</p>
     */
    @Override
    public void connect(String macAddress) {
        readyTimeout.cancel();
        engine.connect(macAddress);
    }

    @Override
    public void disconnect() {
        readyTimeout.cancel();
        engine.disconnect();
    }

    @Override
    public void close() {
        readyTimeout.cancel();
        engine.release();
    }

    @Override
    public boolean isReady() {
        return engine.isReady();
    }

    @Override
    public HsBattery battery() {
        return engine.battery();
    }

    @Override
    public void setDeviceObserver(@Nullable HsDeviceObserver observer) {
        if (observer == null) {
            engine.setDeviceObserver(null);
            return;
        }
        engine.setDeviceObserver(new MainDeviceObserver(observer));
    }

    @Override
    public void setConnectionObserver(@Nullable HsConnectionObserver observer) {
        connectionEvents.setDelegate(observer);
    }

    /**
     * 就绪看门狗只报告「这次尝试超时了」。是否收掉链路、是否发 {@code onConnectFailed}，
     * 由引擎按生命周期状态统一决定——会话层不是第二个生命周期事件发送点。
     */
    private void onConnectReadyTimeout() {
        engine.onConnectAttemptTimedOut();
    }

    @Override
    public void setRemoteStreamStop(@Nullable Runnable trigger) {
        if (trigger == null) {
            engine.setRemoteStreamStop(null);
            return;
        }
        engine.setRemoteStreamStop(() -> mainThread.post(trigger));
    }

    @Override
    public void setEqMode(int mode, @Nullable HsResultCallback callback) {
        engine.submit(new EQModeCommand(mode), onMain(callback));
    }

    @Override
    public void setHighSoundQuality(boolean highQuality, @Nullable HsResultCallback callback) {
        engine.submit(new SoundQualityModeCommand(highQuality), onMain(callback));
    }

    @Override
    public void setLdacEnabled(boolean enabled, @Nullable HsResultCallback callback) {
        engine.submit(new LDACCommand(enabled), onMain(callback));
    }

    @Override
    public void setVolumeAdaptive(boolean enabled, @Nullable HsResultCallback callback) {
        engine.submit(new VolumeAdaptiveCommand(enabled), onMain(callback));
    }

    @Override
    public void setGameMode(boolean enabled, @Nullable HsResultCallback callback) {
        engine.submit(new GameModeCommand(enabled), onMain(callback));
    }

    @Override
    public void setWearDetection(boolean enabled, @Nullable HsResultCallback callback) {
        engine.submit(new WearDetectionCommand(enabled), onMain(callback));
    }

    @Override
    public void startFindAlert(boolean leftEar, @Nullable HsResultCallback callback) {
        engine.submit(leftEar ? new FindAlertLeftCommand(true) : new FindAlertRightCommand(true),
                onMain(callback));
    }

    @Override
    public void stopFindAlert(@Nullable HsResultCallback callback) {
        engine.submit(new FindAlertStopCommand(), onMain(callback));
    }

    @Override
    public void readEarColor(@Nullable HsResultCallback callback) {
        engine.submit(new EarColorCommand(EarColorCommand.QueryOrigin.MANUAL), onMain(callback));
    }

    @Override
    public void reboot(@Nullable HsResultCallback callback) {
        engine.submit(new DeviceRebootCommand(), onMain(callback));
    }

    @Nullable
    private HsResultCallback onMain(@Nullable HsResultCallback callback) {
        if (callback == null) {
            return null;
        }
        return result -> mainThread.post(() -> callback.onCompleted(result));
    }

    /**
     * 引擎连接事件的唯一入口：按「尝试开始 / 尝试终态」起表与撤表，同一事件再转给外部观察者。
     *
     * <p>四个回调都由引擎在引擎线程上串行调用（{@code startConnectAttempt} /
     * {@code establishSession} / {@code endSessionAndNotify} / {@code notifyConnectFailedOnce}），
     * 因此 arm 与 cancel 的先后就是引擎的程序顺序，不需要额外的代次或锁：
     * 换目标连接时必然是「旧会话 cancel → 本次尝试 arm」。arm 本身也可以从引擎线程发起：
     * {@link Handler#postDelayed} / {@link Handler#removeCallbacks} 线程安全，
     * {@link ConnectReadyTimeout} 的 armed 标志另有锁保护。</p>
     */
    private final class ConnectionEvents implements HsConnectionObserver {
        private volatile HsConnectionObserver delegate;

        private void setDelegate(@Nullable HsConnectionObserver observer) {
            this.delegate = observer;
        }

        @Override
        public void onConnecting(@NonNull String macAddress) {
            readyTimeout.arm();
            HsConnectionObserver target = delegate;
            if (target != null) {
                mainThread.post(() -> target.onConnecting(macAddress));
            }
        }

        @Override
        public void onConnected(@NonNull String macAddress) {
            readyTimeout.cancel();
            HsConnectionObserver target = delegate;
            if (target != null) {
                mainThread.post(() -> target.onConnected(macAddress));
            }
        }

        @Override
        public void onDisconnected(@Nullable String reason) {
            readyTimeout.cancel();
            HsConnectionObserver target = delegate;
            if (target != null) {
                mainThread.post(() -> target.onDisconnected(reason));
            }
        }

        @Override
        public void onConnectFailed(@Nullable String reason, boolean willRetry) {
            readyTimeout.cancel();
            HsConnectionObserver target = delegate;
            if (target != null) {
                mainThread.post(() -> target.onConnectFailed(reason, willRetry));
            }
        }
    }

    private final class MainDeviceObserver implements HsDeviceObserver {
        private final HsDeviceObserver delegate;

        private MainDeviceObserver(HsDeviceObserver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onBatteryStateChanged(boolean leftUpdated, boolean rightUpdated, boolean caseUpdated) {
            mainThread.post(() -> delegate.onBatteryStateChanged(leftUpdated, rightUpdated, caseUpdated));
        }

        @Override
        public void onEqMode(int mode) {
            mainThread.post(() -> delegate.onEqMode(mode));
        }

        @Override
        public void onGameMode(boolean enabled) {
            mainThread.post(() -> delegate.onGameMode(enabled));
        }

        @Override
        public void onFindAlert(int code, int state) {
            mainThread.post(() -> delegate.onFindAlert(code, state));
        }

        @Override
        public void onVoiceAssistant(int code, int status) {
            mainThread.post(() -> delegate.onVoiceAssistant(code, status));
        }

        @Override
        public void onBeginnerMode(int earType, int actionType) {
            mainThread.post(() -> delegate.onBeginnerMode(earType, actionType));
        }

        @Override
        public void onEarExchange(int earType) {
            mainThread.post(() -> delegate.onEarExchange(earType));
        }

        @Override
        public void onServiceStatus(Map<String, String> status) {
            mainThread.post(() -> delegate.onServiceStatus(status));
        }

        @Override
        public void onPairedDevices(List<Map<String, String>> devices) {
            mainThread.post(() -> delegate.onPairedDevices(devices));
        }

        @Override
        public void onDeviceInfo(HsDeviceInfo info) {
            mainThread.post(() -> delegate.onDeviceInfo(info));
        }
    }

    /**
     * 主线程投递与看门狗计时共用的唯一调度出口。生产实现是主 {@link Handler}；
     * 用例注入可控实现来显式推进 12 秒并观察投递顺序。
     */
    interface MainThread extends ConnectReadyTimeout.Scheduler {

        void post(@NonNull Runnable task);
    }

    private static final class HandlerMainThread implements MainThread {
        private final Handler handler;

        private HandlerMainThread(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void post(@NonNull Runnable task) {
            handler.post(task);
        }

        @Override
        public void postDelayed(@NonNull Runnable task, long delayMillis) {
            handler.postDelayed(task, delayMillis);
        }

        @Override
        public void removeCallbacks(@NonNull Runnable task) {
            handler.removeCallbacks(task);
        }
    }
}
