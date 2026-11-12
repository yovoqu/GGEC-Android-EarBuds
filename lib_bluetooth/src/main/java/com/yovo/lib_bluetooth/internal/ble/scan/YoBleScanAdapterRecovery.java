package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanObserver;

/**
 * 适配器关闭/恢复的会话暂存：记录被适配器关闭中断的扫描配置与观察者，
 * 适配器重新开启时据此决定是否自动恢复。
 *
 * <p>只决定「是否需要恢复、用哪份配置与观察者恢复」，状态迁移与实际启动由控制器执行。</p>
 */
final class YoBleScanAdapterRecovery {

    /** 待恢复的扫描会话。 */
    record Pending(@NonNull BleScanConfig config, @NonNull BleScanObserver observer) {
    }

    @Nullable
    private BleScanConfig config;
    @Nullable
    private BleScanObserver observer;

    /** 暂存被适配器关闭中断的扫描会话。 */
    void recordInterrupted(@NonNull BleScanConfig config, @NonNull BleScanObserver observer) {
        this.config = config;
        this.observer = observer;
    }

    /**
     * 取出待恢复会话并清空暂存。已有扫描在途（刚发起/等待限流/正在扫描）时不恢复，
     * 避免与在途会话叠成两代扫描。
     *
     * @return 可恢复的会话；无可恢复内容时返回 {@code null}。
     */
    @Nullable
    Pending takeResumable(boolean scanInFlight) {
        BleScanConfig pendingConfig = config;
        BleScanObserver pendingObserver = observer;
        clear();
        if (pendingConfig == null || pendingObserver == null || scanInFlight) {
            return null;
        }
        return new Pending(pendingConfig, pendingObserver);
    }

    void clear() {
        config = null;
        observer = null;
    }
}
