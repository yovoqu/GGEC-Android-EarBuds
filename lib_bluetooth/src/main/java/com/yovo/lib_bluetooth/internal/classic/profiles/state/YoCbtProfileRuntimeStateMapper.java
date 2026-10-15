package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 {@link YoCbtProfileConnectionState} 映射为统一 {@link YoCbtProfileRuntimeState} 词汇。
 */
public final class YoCbtProfileRuntimeStateMapper {

    private YoCbtProfileRuntimeStateMapper() {
    }

    @NonNull
    public static YoCbtProfileRuntimeState fromConnectionState(
            @NonNull YoCbtProfileConnectionState connectionState) {
        switch (connectionState) {
            case DISCONNECTED:
                return YoCbtProfileRuntimeState.DISCONNECTED;
            case CONNECTING:
                return YoCbtProfileRuntimeState.CONNECTING;
            case CONNECTED:
                return YoCbtProfileRuntimeState.CONNECTED;
            case DISCONNECTING:
                return YoCbtProfileRuntimeState.DISCONNECTING;
            case UNKNOWN:
            default:
                return YoCbtProfileRuntimeState.UNKNOWN;
        }
    }
}
