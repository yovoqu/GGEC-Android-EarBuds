package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeScope;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单次发现操作的运行时上下文，持有会话、过滤器与结果缓存（内部使用）。
 */
final class YoCbtDiscoveryOperationContext {

    final YoCbtDiscoverySession eventSession;
    final String operationId;
    final YoCbtDiscoveryConfig config;
    final YoCbtDiscoveryObserver observer;
    final YoCbtDiscoveryFilter filter;
    final AtomicBoolean completed;
    final LinkedHashMap<YoCbtDeviceId, YoCbtDiscoveryResultEntry> results;
    @Nullable YoCbtRuntimeScope scope;
    @Nullable YoTimeoutHandle timeoutHandle;

    YoCbtDiscoveryOperationContext(@NonNull YoCbtDiscoveryConfig config,
                                   @NonNull YoCbtDiscoveryObserver observer) {
        this.eventSession = new YoCbtDiscoverySession(config, observer);
        this.operationId = eventSession.getOperationId();
        this.config = config;
        this.observer = observer;
        this.filter = config.toFilter();
        this.completed = new AtomicBoolean(false);
        this.results = new LinkedHashMap<>();
    }
}
