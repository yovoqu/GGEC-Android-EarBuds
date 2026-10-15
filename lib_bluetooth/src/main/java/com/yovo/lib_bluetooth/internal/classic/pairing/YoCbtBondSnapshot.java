package com.yovo.lib_bluetooth.internal.classic.pairing;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceAlias;
import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备 Bond 状态快照，记录身份、配对状态与时间戳，可转为 {@link YoCbtDeviceSnapshot}。
 */
public final class YoCbtBondSnapshot {

    @NonNull
    private final YoCbtDeviceIdentity identity;

    @NonNull
    private final YoCbtBondState bondState;

    private final long timestampMillis;

    /**
     * @param identity        设备身份
     * @param bondState       快照时刻的配对状态
     * @param timestampMillis 拍摄此快照的时间
     */
    public YoCbtBondSnapshot(@NonNull YoCbtDeviceIdentity identity,
                              @NonNull YoCbtBondState bondState,
                              long timestampMillis) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.bondState = Objects.requireNonNull(bondState, "bondState must not be null");
        this.timestampMillis = timestampMillis;
    }

    /**
     * 返回此快照时刻的设备身份。
     */
    @NonNull
    public YoCbtDeviceIdentity getIdentity() {
        return identity;
    }

    /**
     * 返回从身份中提取的设备 ID。
     */
    @NonNull
    public com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId getDeviceId() {
        return identity.getDeviceId();
    }

    /**
     * 返回此快照时刻的配对状态。
     */
    @NonNull
    public YoCbtBondState getBondState() {
        return bondState;
    }

    /**
     * 返回拍摄此快照时的时间戳。
     */
    public long getTimestampMillis() {
        return timestampMillis;
    }

    /**
     * 若快照时刻设备已配对则返回 {@code true}。
     */
    public boolean isBonded() {
        return bondState == YoCbtBondState.BONDED;
    }

    /**
     * 若快照时刻配对状态为终态（已配对、已移除或失败）则返回 {@code true}。
     */
    public boolean isTerminal() {
        return bondState == YoCbtBondState.BONDED
                || bondState == YoCbtBondState.REMOVED
                || bondState == YoCbtBondState.FAILED;
    }

    /**
     * 将此配对快照转换为完整的 {@link YoCbtDeviceSnapshot}，
     * 便于存入 {@link com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore}。
     *
     * <p>配对快照中不存在的字段（能力、ACL 状态、UUID、Profile 状态等）
     * 以合理默认值填充。
     *
     * @return 非配对字段使用默认值的设备快照
     */
    @NonNull
    public YoCbtDeviceSnapshot toDeviceSnapshot() {
        String displayName = identity.getDisplayName();
        return new YoCbtDeviceSnapshot(
                identity,
                new YoCbtDeviceAlias(displayName != null ? displayName : "", ""),
                YoCbtDeviceClass.unknown(),
                YoCbtDeviceCapability.empty(),
                bondState,
                YoCbtAclLinkState.UNKNOWN,
                Collections.<UUID>emptyList(),
                new EnumMap<YoCbtProfileType, YoCbtDeviceProfileState>(YoCbtProfileType.class),
                null,   // a2dpState
                null,   // headsetState
                Collections.<YoCbtSppConnectionSnapshot>emptyList(),
                Integer.MIN_VALUE,
                0L,     // lastSeenMillis
                0L,     // lastResolvedMillis
                timestampMillis,
                YoCbtCapabilitySource.UNKNOWN
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtBondSnapshot)) return false;
        YoCbtBondSnapshot that = (YoCbtBondSnapshot) o;
        return timestampMillis == that.timestampMillis
                && identity.equals(that.identity)
                && bondState == that.bondState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, bondState, timestampMillis);
    }

    @Override
    public String toString() {
        return "YoCbtBondSnapshot{"
                + "deviceId=" + identity.getDeviceId()
                + ", bondState=" + bondState
                + ", timestampMillis=" + timestampMillis
                + '}';
    }
}
