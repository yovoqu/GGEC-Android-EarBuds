package com.ggec.earbuds.domain.device.model;

/**
 * 一次连接尝试的终态。连接中不是终态，不出现在这里。
 */
public sealed interface ConnectSettlement {

    /** 会话就绪。此时才可以离开扫描页。 */
    record Ready() implements ConnectSettlement {
    }

    /** 这次尝试结束且没有就绪。 */
    record Failed(DeviceFailure failure) implements ConnectSettlement {

        public Failed {
            if (failure == null) {
                throw new IllegalArgumentException("连接失败必须带分类");
            }
        }
    }
}
