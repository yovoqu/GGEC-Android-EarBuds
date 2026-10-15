package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceAlias;
import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 构建 {@link YoCbtDeviceProfileSnapshot}，聚合身份、配对、ACL、Profile 与 SPP 状态。
 */
public final class YoCbtDeviceProfileSnapshotBuilder {

    @Nullable
    private YoCbtDeviceId deviceId;

    @NonNull
    private YoCbtBondState bondState = YoCbtBondState.UNKNOWN;

    @NonNull
    private YoCbtAclLinkState aclState = YoCbtAclLinkState.UNKNOWN;

    @NonNull
    private final Set<YoCbtProfileType> supportedProfiles = EnumSet.noneOf(YoCbtProfileType.class);

    @NonNull
    private final Set<YoCbtProfileType> connectedProfiles = EnumSet.noneOf(YoCbtProfileType.class);

    @Nullable
    private YoCbtA2dpDeviceState a2dpState;

    @Nullable
    private YoCbtHeadsetDeviceState headsetState;

    @NonNull
    private List<YoCbtSppConnectionSnapshot> sppSnapshots = Collections.emptyList();

    // ----- 链式 setter --------------------------------------------------

    /**
     * 设置蓝牙设备标识。
     *
     * @param deviceId 设备标识；不可为 null
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withDeviceId(@NonNull YoCbtDeviceId deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /**
     * 设置设备的配对（bond）状态。
     * <p>
     * 未调用时默认为 {@link YoCbtBondState#UNKNOWN}。
     *
     * @param state 配对状态；不可为 null
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withBondState(@NonNull YoCbtBondState state) {
        this.bondState = state;
        return this;
    }

    /**
     * 设置设备的 ACL 链路状态。
     * <p>
     * 未调用时默认为 {@link YoCbtAclLinkState#UNKNOWN}。
     *
     * @param state ACL 链路状态；不可为 null
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withAclState(@NonNull YoCbtAclLinkState state) {
        this.aclState = state;
        return this;
    }

    /**
     * 向设备支持的 Profile 集合添加一种 Profile 类型。
     * <p>
     * 这些条目用于填充结果快照中的设备能力映射。每种支持的 Profile 调用一次。
     *
     * @param type 支持的 Profile 类型；不可为 null
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder addSupportedProfile(@NonNull YoCbtProfileType type) {
        this.supportedProfiles.add(type);
        return this;
    }

    /**
     * 向设备当前已连接的 Profile 集合添加一种 Profile 类型。
     * <p>
     * 每个已连接 Profile 会在快照的 Profile 状态映射中产生一条
     * {@link YoCbtDeviceProfileState} 条目，状态为
     * {@link YoCbtProfileConnectionState#CONNECTED}。每种已连接 Profile 调用一次。
     * 若同时提供了专用 A2DP 或 Headset 状态对象，其更精确的状态将覆盖此处创建的通用条目。
     *
     * @param type 已连接的 Profile 类型；不可为 null
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder addConnectedProfile(@NonNull YoCbtProfileType type) {
        this.connectedProfiles.add(type);
        return this;
    }

    /**
     * 设置专用 A2DP 设备状态，包含 A2DP 连接状态及运行时编解码/播放状态。
     * <p>
     * 设置后，该状态用于填充 Profile 状态映射中的 A2DP 条目及
     * {@link YoCbtDeviceSnapshot} 上的专用 A2DP 字段。
     *
     * @param state A2DP 设备状态；{@code null} 表示省略
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withA2dpState(@Nullable YoCbtA2dpDeviceState state) {
        this.a2dpState = state;
        return this;
    }

    /**
     * 设置专用 HEADSET 设备状态，包含 Headset 连接状态、音频状态及语音识别标志。
     * <p>
     * 设置后，该状态用于填充 Profile 状态映射中的 HEADSET 条目及
     * {@link YoCbtDeviceSnapshot} 上的专用 Headset 字段。
     *
     * @param state Headset 设备状态；{@code null} 表示省略
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withHeadsetState(@Nullable YoCbtHeadsetDeviceState state) {
        this.headsetState = state;
        return this;
    }

    /**
     * 设置与此设备关联的 SPP 连接快照列表。
     * <p>
     * 未调用时默认为空列表。
     *
     * @param snapshots SPP 连接快照；不可为 null，但可为空
     * @return 本 builder，便于链式调用
     */
    @NonNull
    public YoCbtDeviceProfileSnapshotBuilder withSppConnections(@NonNull List<YoCbtSppConnectionSnapshot> snapshots) {
        this.sppSnapshots = snapshots;
        return this;
    }

    // ----- 构建 -----------------------------------------------------------

    /**
     * 根据累积状态构建 {@link YoCbtDeviceProfileSnapshot}。
     * <p>
     * 构建过程如下：
     * <ol>
     *   <li>从已连接 Profile 集合构建 Profile 状态映射，
     *       再叠加专用 A2DP 与 Headset 状态条目。</li>
     *   <li>从支持的 Profile 集合构建设备能力映射。</li>
     *   <li>根据设备 ID 推导 {@link YoCbtDeviceIdentity}，
     *       对本 builder 无法直接获取的身份字段使用合理默认值。</li>
     *   <li>组装完整的 {@link YoCbtDeviceSnapshot}。</li>
     *   <li>封装为 {@link YoCbtDeviceProfileSnapshot}。</li>
     * </ol>
     *
     * @return 完整填充的 Profile 快照
     * @throws IllegalStateException 若未设置 {@code deviceId}
     */
    @NonNull
    public YoCbtDeviceProfileSnapshot build() {
        if (deviceId == null) {
            throw new IllegalStateException("deviceId must be set via withDeviceId() before build()");
        }

        final long now = System.currentTimeMillis();

        // --- Profile 状态映射 ---------------------------------------------
        EnumMap<YoCbtProfileType, YoCbtDeviceProfileState> profileStatesMap =
                new EnumMap<>(YoCbtProfileType.class);

        // 为每个显式已连接的 Profile 创建 Profile 状态条目。
        for (YoCbtProfileType type : connectedProfiles) {
            profileStatesMap.put(type, new YoCbtDeviceProfileState(
                    deviceId,
                    type,
                    YoCbtProfileConnectionState.CONNECTED,
                    YoCbtProfileStateSource.UNKNOWN,
                    now
            ));
        }

        // 若有专用 A2DP 状态则叠加（可能覆盖上方通用条目，
        // 并提供来自 A2DP 代理的更准确连接状态）。
        if (a2dpState != null) {
            profileStatesMap.put(YoCbtProfileType.A2DP, new YoCbtDeviceProfileState(
                    a2dpState.getDeviceId(),
                    YoCbtProfileType.A2DP,
                    a2dpState.getProfileState(),
                    a2dpState.getSource(),
                    a2dpState.getLastChangedAtMillis()
            ));
        }

        // 若有专用 Headset 状态则叠加（可能覆盖上方通用条目，
        // 并提供来自 Headset 代理的更准确连接状态）。
        if (headsetState != null) {
            profileStatesMap.put(YoCbtProfileType.HEADSET, new YoCbtDeviceProfileState(
                    headsetState.getDeviceId(),
                    YoCbtProfileType.HEADSET,
                    headsetState.getProfileState(),
                    headsetState.getSource(),
                    headsetState.getLastChangedAtMillis()
            ));
        }

        // --- 设备能力映射 -----------------------------------------
        EnumMap<YoCbtProfileType, YoCbtCapabilitySource> capMap =
                new EnumMap<>(YoCbtProfileType.class);
        for (YoCbtProfileType type : supportedProfiles) {
            capMap.put(type, YoCbtCapabilitySource.UNKNOWN);
        }
        YoCbtDeviceCapability capability = capMap.isEmpty()
                ? YoCbtDeviceCapability.empty()
                : new YoCbtDeviceCapability(capMap);

        // --- 设备身份 -----------------------------------------------
        // 使用 deviceId 的规范值作为地址字符串。其他身份字段（addressType、deviceType）
        // 默认为 UNKNOWN，因 builder 无法直接获取 —— 需要精确身份数据的调用方
        // 应自行构造 YoCbtDeviceIdentity 并用其 getDeviceId() 构建快照。
        String address = deviceId.value();
        YoCbtDeviceIdentity identity = new YoCbtDeviceIdentity(
                deviceId,
                address,
                YoCbtAddressType.UNKNOWN,
                YoCbtDeviceType.UNKNOWN,
                /* displayName */ null
        );

        // --- 构建设备快照 -----------------------------------------
        YoCbtDeviceSnapshot deviceSnapshot = new YoCbtDeviceSnapshot(
                identity,
                identity.getAlias(),                     // YoCbtDeviceAlias
                YoCbtDeviceClass.unknown(),              // YoCbtDeviceClass
                capability,                               // YoCbtDeviceCapability
                bondState,                                // YoCbtBondState
                aclState,                                 // YoCbtAclLinkState
                Collections.<UUID>emptyList(),            // discoveredUuids
                profileStatesMap,                         // profileStates
                a2dpState,                                // YoCbtA2dpDeviceState
                headsetState,                             // YoCbtHeadsetDeviceState
                sppSnapshots,                             // List<YoCbtSppConnectionSnapshot>
                Integer.MIN_VALUE,                        // rssi（未知哨兵值）
                0L,                                       // lastSeenMillis
                0L,                                       // lastResolvedMillis
                now,                                      // lastUpdatedMillis
                YoCbtCapabilitySource.UNKNOWN             // YoCbtCapabilitySource
        );

        // --- 封装并返回 -----------------------------------------------
        return new YoCbtDeviceProfileSnapshot(deviceSnapshot);
    }
}
