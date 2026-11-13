package com.ggec.earbuds.ui.device.scan;

import com.ggec.earbuds.domain.device.model.DeviceFailure;

/** 连接尝试的一次性结果。界面据此离开扫描页或提示失败。 */
public sealed interface ScanConnectOutcome {

    record Succeeded() implements ScanConnectOutcome {
    }

    record Failed(DeviceFailure failure) implements ScanConnectOutcome {
    }
}
