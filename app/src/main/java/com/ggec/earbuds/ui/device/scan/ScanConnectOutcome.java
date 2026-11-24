package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceFailure;

import java.util.Objects;

/** 连接尝试的一次性结果。界面据此离开扫描页、打开录播页或提示失败。 */
public sealed interface ScanConnectOutcome {

    record Succeeded() implements ScanConnectOutcome {
    }

    record Failed(DeviceFailure failure) implements ScanConnectOutcome {
    }

    /**
     * 录播机型：不经 DeviceSession，由界面打开 Record 页并带上 MAC。
     */
    record OpenRecord(@NonNull String address, @NonNull String name) implements ScanConnectOutcome {

        public OpenRecord {
            Objects.requireNonNull(address, "address");
            name = name != null ? name : "";
        }
    }
}
