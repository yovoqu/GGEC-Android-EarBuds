package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 台账里的一台设备。
 *
 * <p>身份、别名、状态来源、设备事实、能力值放在一条记录里：这是"这台设备是谁 + 最后/现在怎样"
 * 的唯一载体。实时与档案的差别只体现在 {@link DeviceStatus#origin()}，不用两套类型。</p>
 */
public record DeviceRecord(DeviceId id,
                           String alias,
                           DeviceIdentity identity,
                           DeviceStatus status,
                           DeviceFacts facts,
                           DeviceState state) {

    public DeviceRecord {
        Objects.requireNonNull(id, "id");
        alias = Objects.requireNonNull(alias, "alias").trim();
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(state, "state");
    }

    /** 展示名：用户别名优先，其次广播名，最后型号。 */
    public String displayName() {
        if (!alias.isEmpty()) {
            return alias;
        }
        return identity.bleName().isEmpty() ? id.modelId() : identity.bleName();
    }

    public boolean live() {
        return status.live();
    }

    public DeviceRecord archived(long lastSeenMillis) {
        return new DeviceRecord(id, alias, identity,
                DeviceStatus.archived(lastSeenMillis), facts, state);
    }

    public DeviceRecord live(ConnectionState connection, long nowMillis) {
        return new DeviceRecord(id, alias, identity,
                new DeviceStatus(RecordOrigin.LIVE, connection, nowMillis), facts, state);
    }

    public DeviceRecord renamed(String nextAlias) {
        return new DeviceRecord(id, nextAlias, identity, status, facts, state);
    }
}
