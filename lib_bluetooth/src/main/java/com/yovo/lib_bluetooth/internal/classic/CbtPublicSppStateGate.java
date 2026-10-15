package com.yovo.lib_bluetooth.internal.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;

/**
 * 公开 SPP 状态闸门：内部细粒度阶段折叠为 {@link CbtSppConnectionState} 后，
 * 仅在公开状态真正变化时放行。这是对外唯一状态源，而不是内部生命周期打点。
 */
final class CbtPublicSppStateGate {

    @Nullable
    private CbtSppConnectionState last;

    boolean accept(@NonNull CbtSppConnectionState next) {
        if (next == last) {
            return false;
        }
        last = next;
        return true;
    }
}
