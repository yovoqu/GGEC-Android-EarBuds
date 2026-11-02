package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public interface AiSettingsRepository {

    AiSettings load();

    /**
     * 异步读取当前设置。磁盘实现必须把读取放到后台执行；并发调用共享同一次读取。
     * 首次读取完成前 {@link #ready()} 为 false。
     */
    CompletableFuture<AiSettings> loadAsync();

    /**
     * 原子地读-改-写。测速回调和用户编辑可能同时到达，修改函数在仓库的锁内执行，
     * 所以它必须是快速且无副作用的纯函数。
     */
    AiSettings update(UnaryOperator<AiSettings> change);

    /**
     * 每次设置变化后在调用 {@link #update} 的线程上通知，不会在锁内回调。
     */
    Subscription observe(Consumer<AiSettings> observer);

    /**
     * 持久化状态通知。磁盘实现可能从后台线程回调；观察者必须立即返回。
     */
    default Subscription observePersistenceStatus(
            Consumer<AiSettingsPersistenceStatus> observer) {
        return () -> {
        };
    }

    /**
     * 持久化失败通知。实现可以在后台线程调用；观察者必须立即返回。
     * 默认实现用于没有磁盘后端的测试仓库。
     */
    default Subscription observePersistenceFailures(Consumer<Throwable> observer) {
        return () -> {
        };
    }

    /**
     * 等待截至调用时刻的修改写入完成。生产环境只在退出、诊断或显式保存时使用。
     */
    default CompletableFuture<Void> flush() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 首次读取是否已从存储完成。内存仓库总是就绪；磁盘仓库只在后台预加载成功后返回 true。
     * 调用方用 {@link #observe} 收到首个快照，不能据此猜测读取已经完成。
     */
    default boolean ready() {
        return true;
    }
}
