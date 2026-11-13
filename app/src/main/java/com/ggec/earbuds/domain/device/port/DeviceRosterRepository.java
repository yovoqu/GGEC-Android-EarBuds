package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceRoster;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 设备台账仓库：内存快照 + 变化订阅 + 变更。
 *
 * <p>实现负责持久化（单写者、合并写入），领域层只表达"台账变成什么"。
 * 观察者在调用线程上被通知，实现必须保证同一时刻只有一个通知在跑；
 * 观察者不得回调仓库自身（否则会重入）。</p>
 */
public interface DeviceRosterRepository {

    DeviceRoster current();

    Subscription observe(Consumer<DeviceRoster> listener);

    /** 读写失败（含冷启动解析失败）。 */
    Subscription observeFailures(Consumer<DeviceFailure> listener);

    /** 冷启动读盘失败的一次性结果；成功为空。 */
    Optional<DeviceFailure> startupFailure();

    /** 以当前台账为入参算出新台账；无变化时不写盘、不通知。 */
    void update(UnaryOperator<DeviceRoster> change);
}
