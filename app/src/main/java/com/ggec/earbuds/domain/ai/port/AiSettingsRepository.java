package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiSettings;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public interface AiSettingsRepository {

    AiSettings load();

    /**
     * 原子地读-改-写。测速回调和用户编辑可能同时到达，修改函数在仓库的锁内执行，
     * 所以它必须是快速且无副作用的纯函数。
     */
    AiSettings update(UnaryOperator<AiSettings> change);

    /**
     * 每次设置变化后在调用 {@link #update} 的线程上通知，不会在锁内回调。
     */
    Subscription observe(Consumer<AiSettings> observer);
}
