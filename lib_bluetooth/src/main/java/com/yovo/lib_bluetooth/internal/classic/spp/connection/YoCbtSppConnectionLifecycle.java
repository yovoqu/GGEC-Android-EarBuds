package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 单连接唯一生命周期。所有对外可见的 phase 只允许从这里写入。
 */
public final class YoCbtSppConnectionLifecycle {

    private static final String TAG = "YoCbtSppLifecycle";

    private final YoCbtSppConnectionId connectionId;
    private final AtomicReference<YoCbtSppConnectionState> currentPhase;
    private volatile boolean readerStarted;
    private volatile boolean writerStarted;
    private volatile boolean connected;
    private volatile boolean disconnecting;
    private volatile boolean disconnected;
    private volatile boolean failed;
    private volatile YoCbtFailure pendingFailure;

    public YoCbtSppConnectionLifecycle(@NonNull YoCbtSppConnectionId connectionId) {
        this.connectionId = connectionId;
        this.currentPhase = new AtomicReference<>(YoCbtSppConnectionState.IDLE);
    }

    /**
     * 唯一写入点：更新 phase 与派生标志，随后由连接对象发布 snapshot()。
     */
    public void enter(@NonNull YoCbtSppConnectionState phase, @Nullable YoCbtFailure failure) {
        if (failure != null) {
            pendingFailure = failure;
        }
        switch (phase) {
            case STARTING_READER:
                readerStarted = true;
                break;
            case STARTING_WRITER:
                writerStarted = true;
                break;
            case CONNECTED:
                connected = true;
                disconnecting = false;
                disconnected = false;
                failed = false;
                break;
            case DISCONNECTING:
                disconnecting = true;
                connected = false;
                break;
            case DISCONNECTED:
            case RELEASED:
                disconnected = true;
                connected = false;
                disconnecting = false;
                break;
            case FAILED:
                failed = true;
                connected = false;
                disconnecting = false;
                break;
            default:
                break;
        }
        YoCbtSppConnectionState previous = currentPhase.getAndSet(phase);
        if (previous != phase) {
            YoBluetoothLogger.d(TAG, "连接 " + connectionId + " 阶段：" + previous + " -> " + phase);
        }
    }

    public boolean isActive() {
        return connected && !disconnecting && !disconnected && !failed;
    }

    public boolean canStartReader() {
        return !readerStarted && !disconnected && !failed;
    }

    public boolean canStartWriter() {
        return !writerStarted && !disconnected && !failed;
    }

    @NonNull
    public YoCbtSppConnectionState getCurrentPhase() {
        return currentPhase.get();
    }

    @Nullable
    public YoCbtFailure getFailure() {
        return pendingFailure;
    }
}
