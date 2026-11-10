package com.yovo.biz_ggec_hs.internal.session;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.internal.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.FirmwareVersionCommand;
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
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsResultCallback;

import java.util.List;
import java.util.Map;

/**
 * 公开会话的实现。只组装协议命令，并把回调切回主线程。
 */
public final class YoHsClientImpl implements YoHsClient {
    private final HsProtocolEngine engine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public YoHsClientImpl(HsProtocolEngine engine) {
        this.engine = engine;
    }

    @Override
    public void connect(String macAddress) {
        engine.connect(macAddress);
    }

    @Override
    public void disconnect() {
        engine.disconnect();
    }

    @Override
    public void close() {
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
    public void setRemoteStreamStop(@Nullable Runnable trigger) {
        if (trigger == null) {
            engine.setRemoteStreamStop(null);
            return;
        }
        engine.setRemoteStreamStop(() -> mainHandler.post(trigger));
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
    public void readFirmwareVersion(@Nullable HsResultCallback callback) {
        engine.submit(new FirmwareVersionCommand(), onMain(callback));
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
        return result -> mainHandler.post(() -> callback.onCompleted(result));
    }

    private final class MainDeviceObserver implements HsDeviceObserver {
        private final HsDeviceObserver delegate;

        private MainDeviceObserver(HsDeviceObserver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onBatteryStateChanged(boolean leftUpdated, boolean rightUpdated, boolean caseUpdated) {
            mainHandler.post(() -> delegate.onBatteryStateChanged(leftUpdated, rightUpdated, caseUpdated));
        }

        @Override
        public void onEqMode(int mode) {
            mainHandler.post(() -> delegate.onEqMode(mode));
        }

        @Override
        public void onGameMode(boolean enabled) {
            mainHandler.post(() -> delegate.onGameMode(enabled));
        }

        @Override
        public void onFindAlert(int code, int state) {
            mainHandler.post(() -> delegate.onFindAlert(code, state));
        }

        @Override
        public void onVoiceAssistant(int code, int status) {
            mainHandler.post(() -> delegate.onVoiceAssistant(code, status));
        }

        @Override
        public void onBeginnerMode(int earType, int actionType) {
            mainHandler.post(() -> delegate.onBeginnerMode(earType, actionType));
        }

        @Override
        public void onEarExchange(int earType) {
            mainHandler.post(() -> delegate.onEarExchange(earType));
        }

        @Override
        public void onServiceStatus(Map<String, String> status) {
            mainHandler.post(() -> delegate.onServiceStatus(status));
        }

        @Override
        public void onPairedDevices(List<Map<String, String>> devices) {
            mainHandler.post(() -> delegate.onPairedDevices(devices));
        }
    }
}
