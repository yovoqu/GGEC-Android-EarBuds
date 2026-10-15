package com.yovo.lib_bluetooth.internal.classic.testing;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 模拟 SDP UUID 查询的 Fake 源，可预配置设备 UUID 并异步回调观察者。
 */
public final class FakeYoCbtSdpSource {

    private final ConcurrentHashMap<YoCbtDeviceId, List<UUID>> uuidMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<YoCbtDeviceId, YoCbtDeviceIdentity> identityMap =
            new ConcurrentHashMap<>();

    private volatile long responseDelayMs = 100; // 默认模拟延迟
    private volatile boolean simulateFailure;
    private volatile YoCbtFailure failureOverride;

    /**
     * 将一组 UUID 与设备关联，模拟 SDP 查询成功结果。
     *
     * @param deviceId 设备标识
     * @param uuids    设备应报告的 UUID；可为空列表表示未发现服务
     */
    public void setUuids(@NonNull YoCbtDeviceId deviceId, @NonNull List<UUID> uuids) {
        if (deviceId == null || uuids == null) return;
        uuidMap.put(deviceId, Collections.unmodifiableList(new ArrayList<>(uuids)));
    }

    /**
     * 将设备身份与设备 ID 关联，以便 Fake 构造有意义的快照。
     *
     * @param deviceId 设备标识
     * @param identity 要关联的设备身份
     */
    public void setIdentity(@NonNull YoCbtDeviceId deviceId,
                            @NonNull YoCbtDeviceIdentity identity) {
        if (deviceId == null || identity == null) return;
        identityMap.put(deviceId, identity);
    }

    /**
     * 移除给定设备的所有已配置 UUID 与身份。
     *
     * @param deviceId 要清除的设备
     */
    public void removeDevice(@NonNull YoCbtDeviceId deviceId) {
        uuidMap.remove(deviceId);
        identityMap.remove(deviceId);
    }

    /**
     * 清除所有已配置数据。
     */
    public void clear() {
        uuidMap.clear();
        identityMap.clear();
    }

    /**
     * 返回设备当前已配置的 UUID；未配置时返回空列表。
     */
    @NonNull
    public List<UUID> getUuids(@NonNull YoCbtDeviceId deviceId) {
        List<UUID> uuids = uuidMap.get(deviceId);
        return uuids != null ? uuids : Collections.<UUID>emptyList();
    }

    // ── 失败模拟 ───────────────────────────────────────────

    /**
     * 启用后，下一次 {@link #fetchUuids} 调用将向观察者投递失败而非已配置 UUID。
     *
     * @param fail {@code true} 表示模拟 SDP 失败
     */
    public void setSimulateFailure(boolean fail) {
        this.simulateFailure = fail;
    }

    /**
     * 设置要投递的自定义失败，覆盖默认超时失败。
     *
     * @param failure 要投递的失败；{@code null} 时使用默认失败
     */
    public void setFailureOverride(YoCbtFailure failure) {
        this.failureOverride = failure;
    }

    // ── 时序 ───────────────────────────────────────────────────────

    /**
     * 设置 SDP 查询的模拟响应延迟。
     *
     * @param delayMs 延迟（毫秒），须为非负数
     */
    public void setResponseDelayMs(long delayMs) {
        this.responseDelayMs = Math.max(0, delayMs);
    }

    // ── 查询模拟 ─────────────────────────────────────────────

    /**
     * 模拟通过 SDP 获取设备 UUID。
     *
     * <p>在配置的 {@link #setResponseDelayMs 模拟延迟} 后，于主线程向观察者投递结果或失败。
     *
     * @param deviceId 要查询的设备
     * @param observer 接收结果的观察者
     */
    public void fetchUuids(@NonNull YoCbtDeviceId deviceId,
                           @NonNull YoCbtServiceDiscoveryObserver observer) {
        if (deviceId == null) {
            if (observer != null) {
                observer.onServiceDiscoveryFailed(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_INTERNAL_ERROR,
                        "deviceId must not be null",
                        "FakeYoCbtSdpSource"));
            }
            return;
        }
        if (observer == null) {
            return;
        }

        // 模拟延迟后在主线程投递
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(() -> {
            if (simulateFailure) {
                YoCbtFailure failure = failureOverride != null
                        ? failureOverride
                        : YoCbtFailure.of(
                                YoCbtErrorCode.YO_CBT_SDP_TIMEOUT,
                                "Fake SDP query timed out for " + deviceId,
                                "FakeYoCbtSdpSource");
                observer.onServiceDiscoveryFailed(failure);
                return;
            }

            List<UUID> uuids = uuidMap.get(deviceId);
            if (uuids == null) {
                uuids = Collections.emptyList();
            }

            YoCbtDeviceIdentity identity = identityMap.get(deviceId);
            if (identity == null) {
                // 根据设备 ID 构造最小身份
                identity = new com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity(
                        deviceId,
                        deviceId.value(),
                        com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType.UNKNOWN,
                        com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType.UNKNOWN,
                        "");
            }

            long now = System.currentTimeMillis();
            YoCbtDeviceSnapshot snapshot = new YoCbtDeviceSnapshot(
                    identity,
                    YoCbtBondState.UNKNOWN,
                    YoCbtAclLinkState.UNKNOWN,
                    uuids,
                    new EnumMap<>(YoCbtProfileType.class),
                    new ArrayList<>(),
                    0,
                    now,
                    now);

            observer.onServiceDiscoveryResolved(snapshot);
        }, responseDelayMs);
    }

    /**
     * 返回当前已配置设备的数量。
     */
    public int deviceCount() {
        return uuidMap.size();
    }
}
