package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地托管 GATT Server 配置。
 */
public final class BleGattServerConfig {

    @NonNull
    private final List<BleGattServerService> services;
    private final boolean autoHandleClientValues;

    private BleGattServerConfig(Builder builder) {
        this.services = Collections.unmodifiableList(new ArrayList<>(builder.services));
        this.autoHandleClientValues = builder.autoHandleClientValues;
    }

    @NonNull
    public List<BleGattServerService> services() {
        return services;
    }

    /** 是否由 SDK 自动托管特征/描述符读写缓存与 PreparedWrite 缓冲应答（默认 true）。 */
    public boolean autoHandleClientValues() {
        return autoHandleClientValues;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<BleGattServerService> services = new ArrayList<>();
        private boolean autoHandleClientValues = true;

        @NonNull
        public Builder addService(@NonNull BleGattServerService service) {
            this.services.add(Objects.requireNonNull(service, "service"));
            return this;
        }

        @NonNull
        public Builder autoHandleClientValues(boolean autoHandleClientValues) {
            this.autoHandleClientValues = autoHandleClientValues;
            return this;
        }

        @NonNull
        public BleGattServerConfig build() {
            return new BleGattServerConfig(this);
        }
    }
}
