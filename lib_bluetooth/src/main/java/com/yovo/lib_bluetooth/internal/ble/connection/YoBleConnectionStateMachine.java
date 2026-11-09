package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.state.YoStateMachine;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;

/**
 * BLE 单会话 10 态有限状态机包装：基于 {@link YoStateMachine} 强制合法迁移，禁止越级跳跃。
 */
public final class YoBleConnectionStateMachine {

    private final YoStateMachine<BleConnectionState> delegate;

    public YoBleConnectionStateMachine() {
        this.delegate = YoStateMachine.builder(BleConnectionState.IDLE)
                .allowFrom(BleConnectionState.IDLE,
                        BleConnectionState.CONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.CONNECTING,
                        BleConnectionState.CONNECTED,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.CONNECTED,
                        BleConnectionState.BONDING_WAIT,
                        BleConnectionState.DISCOVERING_SERVICES,
                        BleConnectionState.INITIALIZING,
                        BleConnectionState.READY,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.BONDING_WAIT,
                        BleConnectionState.DISCOVERING_SERVICES,
                        BleConnectionState.INITIALIZING,
                        BleConnectionState.READY,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.DISCOVERING_SERVICES,
                        BleConnectionState.INITIALIZING,
                        BleConnectionState.READY,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.INITIALIZING,
                        BleConnectionState.READY,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.READY,
                        BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.DISCONNECTING,
                        BleConnectionState.DISCONNECTED,
                        BleConnectionState.FAILED)
                .allowFrom(BleConnectionState.DISCONNECTED,
                        BleConnectionState.CONNECTING)
                .allowFrom(BleConnectionState.FAILED,
                        BleConnectionState.CONNECTING,
                        BleConnectionState.DISCONNECTED)
                .build();
    }

    @NonNull
    public BleConnectionState state() {
        return delegate.state();
    }

    @Nullable
    public BleConnectionState previousState() {
        return delegate.previousState();
    }

    public boolean canTransitionTo(@NonNull BleConnectionState next) {
        return delegate.canTransitionTo(next);
    }

    public boolean transitionTo(@NonNull BleConnectionState next, @Nullable Object event) {
        BleConnectionState before = delegate.state();
        if (before == next) {
            return false;
        }
        BleConnectionState after = delegate.transitionTo(next, event);
        return after == next && before != after;
    }

    public boolean isConnected() {
        return delegate.isAny(
                BleConnectionState.CONNECTED,
                BleConnectionState.BONDING_WAIT,
                BleConnectionState.DISCOVERING_SERVICES,
                BleConnectionState.INITIALIZING,
                BleConnectionState.READY);
    }

    public boolean isReady() {
        return delegate.is(BleConnectionState.READY);
    }
}
