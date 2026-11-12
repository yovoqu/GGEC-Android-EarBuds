package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 平台扫描专用串行执行器：{@code BluetoothLeScanner.start/stop/flush} 的 binder 调用与硬件批量能力查询
 * 都在这一条线程上 FIFO 执行，避免底层阻塞冻结唯一的 BLE state 线程（扫描/连接/GATT 全排队）。
 *
 * <p>生命周期由控制器驱动：构造即创建，释放时 {@link #shutdown()}。</p>
 */
final class YoBleScanPlatformExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yo-ble-scan-platform");
        thread.setDaemon(true);
        return thread;
    });

    void submit(@NonNull Runnable task) {
        executor.execute(task);
    }

    void shutdown() {
        executor.shutdown();
    }
}
