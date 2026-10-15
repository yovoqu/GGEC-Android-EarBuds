package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileResolver;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 SDP 服务 UUID 映射为 {@link YoCbtProfileType}，委托 {@link YoCbtProfileResolver} 作为权威来源。
 */
public final class YoCbtProfileUuidMapper {

    @NonNull
    public Set<YoCbtProfileType> mapProfiles(@NonNull List<UUID> uuids) {
        return YoCbtProfileResolver.resolveFromUuids(uuids);
    }
}
