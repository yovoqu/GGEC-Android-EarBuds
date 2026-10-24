package com.ggec.earbuds.domain.ai.port;

import java.util.function.Consumer;

/**
 * 网络是否可用（CHAT-12、CON-04）。实现负责把系统回调折成"在线/离线"两态，只在变化时通知。
 */
public interface AiNetworkMonitor {

    boolean online();

    /** 订阅后不立即回调；回调线程不做保证。 */
    Subscription observe(Consumer<Boolean> observer);

    /** 测试与无网络能力的环境用：永远在线。 */
    AiNetworkMonitor ALWAYS_ONLINE = new AiNetworkMonitor() {
        @Override
        public boolean online() {
            return true;
        }

        @Override
        public Subscription observe(Consumer<Boolean> observer) {
            return () -> {
            };
        }
    };
}
