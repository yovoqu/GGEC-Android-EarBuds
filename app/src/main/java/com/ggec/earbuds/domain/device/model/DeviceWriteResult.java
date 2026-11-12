package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 一次写入 / 动作的结果。成功无载荷，失败只带分类。 */
public sealed interface DeviceWriteResult {

    record Ok() implements DeviceWriteResult {
    }

    record Failed(DeviceFailure failure) implements DeviceWriteResult {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
