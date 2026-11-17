package com.yovo.biz_ggec_wy.internal.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;

/**
 * WY 会话状态记录。
 */
final class WySessionState {

    @NonNull
    private final WyControl.State state;
    @Nullable
    private final WyControl.Failure failure;

    private WySessionState(@NonNull WyControl.State state, @Nullable WyControl.Failure failure) {
        this.state = state;
        this.failure = failure;
    }

    static WySessionState idle() {
        return new WySessionState(WyControl.State.IDLE, null);
    }

    static WySessionState connecting() {
        return new WySessionState(WyControl.State.CONNECTING, null);
    }

    static WySessionState ready() {
        return new WySessionState(WyControl.State.READY, null);
    }

    static WySessionState disconnected(@Nullable WyControl.Failure failure) {
        return new WySessionState(WyControl.State.DISCONNECTED, failure);
    }

    @NonNull
    WyControl.State state() {
        return state;
    }

    @Nullable
    WyControl.Failure failure() {
        return failure;
    }
}
