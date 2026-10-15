package com.yovo.lib_bluetooth.internal.classic.service.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalOperationHandle;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEventHub;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeScope;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtLiveSdpPort;
import com.yovo.lib_bluetooth.internal.classic.service.cache.YoCbtServiceCache;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRequest;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtServiceResolvedSnapshot;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtSdpResolver;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 具有显式状态机和缓存回退机制的服务发现编排器。
 */
public final class YoCbtServiceDiscoveryClient implements YoCbtLiveSdpPort.Listener {

    private static final String TAG = "YoCbtServiceDiscoveryClient";

    private final YoCbtRuntime runtime;
    private final YoCbtLiveSdpPort liveSdpPort;
    private final YoCbtSdpResolver sdpResolver;
    private final YoCbtServiceCache serviceCache;
    private final Map<YoCbtDeviceId, ActiveSession> activeSessions = new ConcurrentHashMap<>();
    private volatile YoCbtServiceDiscoveryState state = YoCbtServiceDiscoveryState.IDLE;
    private volatile boolean released;

    public YoCbtServiceDiscoveryClient(@NonNull YoCbtRuntime runtime,
                                       @NonNull YoCbtLiveSdpPort liveSdpPort,
                                       @NonNull YoCbtSdpResolver sdpResolver,
                                       @NonNull YoCbtServiceCache serviceCache) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.liveSdpPort = Objects.requireNonNull(liveSdpPort, "liveSdpPort must not be null");
        this.sdpResolver = Objects.requireNonNull(sdpResolver, "sdpResolver must not be null");
        this.serviceCache = Objects.requireNonNull(serviceCache, "serviceCache must not be null");
        this.liveSdpPort.setListener(this);
    }

    @NonNull
    public CbtInternalOperationHandle discover(@NonNull YoCbtDeviceId deviceId,
                                         @NonNull YoCbtServiceDiscoveryConfig config,
                                         @NonNull YoCbtServiceDiscoveryObserver observer) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(observer, "observer must not be null");

        if (released) {
            runtime.callback(() -> observer.onServiceDiscoveryFailed(
                    YoCbtFailure.of(YoCbtErrorCode.YO_CBT_RELEASED, "Service discovery client released", TAG)));
            return new CbtInternalOperationHandle("service-released", () -> {});
        }

        YoCbtSdpRequest request = config.toRequest(deviceId);
        cancel(deviceId);

        YoCbtRuntimeScope scope = runtime.createScope();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CbtInternalOperationHandle handle = CbtInternalOperationHandle.scoped(
                "service-" + deviceId + "-" + System.nanoTime(),
                () -> cancelSession(deviceId, cancelled),
                scope);

        ActiveSession session = new ActiveSession(deviceId, request, observer, handle, scope, cancelled);
        activeSessions.put(deviceId, session);

        transition(session, YoCbtServiceDiscoveryState.PREPARING);
        publish(YoCbtServiceDiscoveryEvent.Kind.STARTED, deviceId);
        runtime.callback(() -> {
            if (!cancelled.get()) observer.onServiceDiscoveryStarted();
        });
        transition(session, YoCbtServiceDiscoveryState.DISCOVERING);
        liveSdpPort.startDiscovery(deviceId, request.getTimeoutMillis(), runtime.scheduler());
        return handle;
    }

    public void cancel(@NonNull YoCbtDeviceId deviceId) {
        ActiveSession session = activeSessions.remove(deviceId);
        if (session == null) return;
        session.cancelled.set(true);
        session.scope.release();
        liveSdpPort.cancel(deviceId);
        transition(session, YoCbtServiceDiscoveryState.IDLE);
    }

    public void cancelAll() {
        for (YoCbtDeviceId deviceId : activeSessions.keySet()) {
            cancel(deviceId);
        }
        liveSdpPort.cancelAll();
    }

    public void release() {
        if (released) return;
        released = true;
        cancelAll();
        liveSdpPort.setListener(null);
        state = YoCbtServiceDiscoveryState.RELEASED;
    }

    @Override
    public void onLiveSdpResult(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtSdpResult result) {
        runtime.state(() -> handleLiveSdpResult(deviceId, result));
    }

    @Override
    public void onLiveSdpFailed(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtFailure failure) {
        runtime.state(() -> handleLiveSdpFailed(deviceId, failure));
    }

    private void handleLiveSdpResult(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtSdpResult result) {
        ActiveSession session = activeSessions.remove(deviceId);
        if (session == null || session.cancelled.get()) return;
        finishWithResult(session, result);
    }

    private void handleLiveSdpFailed(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtFailure failure) {
        ActiveSession session = activeSessions.get(deviceId);
        if (session == null || session.cancelled.get()) return;

        if (failure.getErrorCode() == YoCbtErrorCode.YO_CBT_SDP_TIMEOUT
                && session.request.isAllowCacheFallback()) {
            YoCbtSdpResult fallback = serviceCache.getFallback(deviceId);
            if (fallback != null) {
                activeSessions.remove(deviceId);
                YoCbtSdpResult cacheResult = new YoCbtSdpResult(
                        fallback.getDeviceId(),
                        fallback.getServiceRecords(),
                        YoCbtSdpSource.CACHE_FALLBACK,
                        System.currentTimeMillis());
                publish(YoCbtServiceDiscoveryEvent.Kind.CACHE_FALLBACK, deviceId);
                finishWithResult(session, cacheResult);
                return;
            }
        }

        activeSessions.remove(deviceId);
        YoCbtServiceDiscoveryState terminal = failure.getErrorCode() == YoCbtErrorCode.YO_CBT_SDP_TIMEOUT
                ? YoCbtServiceDiscoveryState.TIMEOUT
                : YoCbtServiceDiscoveryState.FAILED;
        transition(session, terminal);
        publish(failure.getErrorCode() == YoCbtErrorCode.YO_CBT_SDP_TIMEOUT
                ? YoCbtServiceDiscoveryEvent.Kind.TIMEOUT : YoCbtServiceDiscoveryEvent.Kind.FAILED, deviceId);
        runtime.callback(() -> session.observer.onServiceDiscoveryFailed(failure));
        session.scope.release();
    }

    private void finishWithResult(@NonNull ActiveSession session, @NonNull YoCbtSdpResult result) {
        YoResult<YoCbtServiceResolvedSnapshot> resolved = sdpResolver.resolve(session.request, result);
        if (resolved.isFailure()) {
            YoCbtServiceDiscoveryState terminal = result.isEmpty()
                    ? YoCbtServiceDiscoveryState.EMPTY
                    : YoCbtServiceDiscoveryState.FAILED;
            transition(session, terminal);
            publish(result.isEmpty() ? YoCbtServiceDiscoveryEvent.Kind.EMPTY : YoCbtServiceDiscoveryEvent.Kind.FAILED,
                    session.deviceId);
            YoCbtFailure failure = (YoCbtFailure) resolved.getFailure();
            runtime.callback(() -> session.observer.onServiceDiscoveryFailed(failure));
        } else {
            transition(session, result.isEmpty()
                    ? YoCbtServiceDiscoveryState.EMPTY
                    : YoCbtServiceDiscoveryState.RESOLVED);
            publish(result.isEmpty() ? YoCbtServiceDiscoveryEvent.Kind.EMPTY : YoCbtServiceDiscoveryEvent.Kind.RESOLVED,
                    session.deviceId);
            YoCbtServiceResolvedSnapshot snapshot = resolved.getValue();
            runtime.callback(() -> session.observer.onServiceDiscoveryResolved(snapshot.getDeviceSnapshot()));
        }
        session.scope.release();
    }

    private void cancelSession(@NonNull YoCbtDeviceId deviceId, @NonNull AtomicBoolean cancelled) {
        cancelled.set(true);
        cancel(deviceId);
    }

    private void transition(@NonNull ActiveSession session, @NonNull YoCbtServiceDiscoveryState newState) {
        state = newState;
        runtime.callback(() -> session.observer.onServiceDiscoveryStateChanged(newState));
    }

    private void publish(@NonNull YoCbtServiceDiscoveryEvent.Kind kind, @NonNull YoCbtDeviceId deviceId) {
        YoCbtEventHub hub = runtime.eventHub();
        hub.publish(YoCbtEvents.SDP, new YoCbtServiceDiscoveryEvent(kind, deviceId));
    }

    private static final class ActiveSession {
        final YoCbtDeviceId deviceId;
        final YoCbtSdpRequest request;
        final YoCbtServiceDiscoveryObserver observer;
        final CbtInternalOperationHandle handle;
        final YoCbtRuntimeScope scope;
        final AtomicBoolean cancelled;

        ActiveSession(YoCbtDeviceId deviceId,
                      YoCbtSdpRequest request,
                      YoCbtServiceDiscoveryObserver observer,
                      CbtInternalOperationHandle handle,
                      YoCbtRuntimeScope scope,
                      AtomicBoolean cancelled) {
            this.deviceId = deviceId;
            this.request = request;
            this.observer = observer;
            this.handle = handle;
            this.scope = scope;
            this.cancelled = cancelled;
        }
    }
}
