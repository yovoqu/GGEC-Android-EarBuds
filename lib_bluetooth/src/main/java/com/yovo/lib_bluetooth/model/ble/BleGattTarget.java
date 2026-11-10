package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * GATT 读写与订阅目标，支持同 UUID 多服务/多特征实例 ID（instanceId）精确寻址与描述符操作。
 */
public final class BleGattTarget {

    public static final int UNSPECIFIED_INSTANCE_ID = -1;

    @NonNull
    private final UUID serviceUuid;
    private final int serviceInstanceId;
    @NonNull
    private final UUID characteristicUuid;
    private final int characteristicInstanceId;
    @Nullable
    private final UUID descriptorUuid;

    private BleGattTarget(@NonNull UUID serviceUuid,
                          int serviceInstanceId,
                          @NonNull UUID characteristicUuid,
                          int characteristicInstanceId,
                          @Nullable UUID descriptorUuid) {
        this.serviceUuid = Objects.requireNonNull(serviceUuid, "serviceUuid");
        this.serviceInstanceId = serviceInstanceId;
        this.characteristicUuid = Objects.requireNonNull(characteristicUuid, "characteristicUuid");
        this.characteristicInstanceId = characteristicInstanceId;
        this.descriptorUuid = descriptorUuid;
    }

    @NonNull
    public static BleGattTarget characteristic(@NonNull UUID serviceUuid,
                                               @NonNull UUID characteristicUuid) {
        return new BleGattTarget(serviceUuid, UNSPECIFIED_INSTANCE_ID,
                characteristicUuid, UNSPECIFIED_INSTANCE_ID, null);
    }

    @NonNull
    public static BleGattTarget characteristic(@NonNull UUID serviceUuid,
                                               int serviceInstanceId,
                                               @NonNull UUID characteristicUuid,
                                               int characteristicInstanceId) {
        return new BleGattTarget(serviceUuid, serviceInstanceId,
                characteristicUuid, characteristicInstanceId, null);
    }

    @NonNull
    public static BleGattTarget descriptor(@NonNull UUID serviceUuid,
                                           @NonNull UUID characteristicUuid,
                                           @NonNull UUID descriptorUuid) {
        return new BleGattTarget(serviceUuid, UNSPECIFIED_INSTANCE_ID,
                characteristicUuid, UNSPECIFIED_INSTANCE_ID,
                Objects.requireNonNull(descriptorUuid, "descriptorUuid"));
    }

    @NonNull
    public static BleGattTarget descriptor(@NonNull UUID serviceUuid,
                                           int serviceInstanceId,
                                           @NonNull UUID characteristicUuid,
                                           int characteristicInstanceId,
                                           @NonNull UUID descriptorUuid) {
        return new BleGattTarget(serviceUuid, serviceInstanceId,
                characteristicUuid, characteristicInstanceId,
                Objects.requireNonNull(descriptorUuid, "descriptorUuid"));
    }

    @NonNull
    public UUID serviceUuid() {
        return serviceUuid;
    }

    public int serviceInstanceId() {
        return serviceInstanceId;
    }

    @NonNull
    public UUID characteristicUuid() {
        return characteristicUuid;
    }

    public int characteristicInstanceId() {
        return characteristicInstanceId;
    }

    @Nullable
    public UUID descriptorUuid() {
        return descriptorUuid;
    }

    public boolean isDescriptorTarget() {
        return descriptorUuid != null;
    }

    /**
     * 完成回调与排队操作的宽松匹配：UUID 必须相同；任一侧 instanceId 未指定则不比较；
     * 描述符 UUID 两边都为空视为特征操作，否则必须相等。
     */
    public boolean matches(@NonNull BleGattTarget other) {
        if (!serviceUuid.equals(other.serviceUuid) || !characteristicUuid.equals(other.characteristicUuid)) {
            return false;
        }
        if (serviceInstanceId >= 0 && other.serviceInstanceId >= 0
                && serviceInstanceId != other.serviceInstanceId) {
            return false;
        }
        if (characteristicInstanceId >= 0 && other.characteristicInstanceId >= 0
                && characteristicInstanceId != other.characteristicInstanceId) {
            return false;
        }
        return Objects.equals(descriptorUuid, other.descriptorUuid);
    }

    /** 忽略具体 descriptorUuid，返回归属特征层的寻址键。 */
    @NonNull
    public BleGattTarget withoutDescriptor() {
        if (descriptorUuid == null) {
            return this;
        }
        return new BleGattTarget(serviceUuid, serviceInstanceId,
                characteristicUuid, characteristicInstanceId, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleGattTarget)) return false;
        BleGattTarget that = (BleGattTarget) o;
        return serviceInstanceId == that.serviceInstanceId
                && characteristicInstanceId == that.characteristicInstanceId
                && serviceUuid.equals(that.serviceUuid)
                && characteristicUuid.equals(that.characteristicUuid)
                && Objects.equals(descriptorUuid, that.descriptorUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceUuid, serviceInstanceId,
                characteristicUuid, characteristicInstanceId, descriptorUuid);
    }

    @NonNull
    @Override
    public String toString() {
        return "BleGattTarget{"
                + "svc=" + serviceUuid + (serviceInstanceId >= 0 ? "#" + serviceInstanceId : "")
                + ", chr=" + characteristicUuid
                + (characteristicInstanceId >= 0 ? "#" + characteristicInstanceId : "")
                + (descriptorUuid != null ? ", desc=" + descriptorUuid : "")
                + '}';
    }
}
