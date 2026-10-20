package com.ggec.earbuds.domain.ai.port;

/**
 * 一次进行中的网络操作。取消之后网关不再回调。
 */
public interface AiCancellable {

    AiCancellable NONE = () -> { };

    void cancel();
}
