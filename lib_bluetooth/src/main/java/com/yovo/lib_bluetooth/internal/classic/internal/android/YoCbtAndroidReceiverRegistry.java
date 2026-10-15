package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 管理 Android {@link BroadcastReceiver} 注册与注销，线程安全且幂等。
 */
public final class YoCbtAndroidReceiverRegistry {

    private static final String TAG = "YoCbtAndroidReceiverRegistry";

    private final CopyOnWriteArrayList<ReceiverEntry> receivers =
            new CopyOnWriteArrayList<>();

    /**
     * 使用给定过滤器注册 {@link BroadcastReceiver}。
     *
     * <p>若同一 receiver 实例已注册，则本调用为
     * 空操作（幂等）。
     *
     * @param context  应用上下文
     * @param receiver 要注册的广播接收器
     * @param filter   匹配的 Intent 过滤器
     * @return 若为新注册则返回 {@code true}，若已注册则返回 {@code false}
     * @throws IllegalArgumentException 若 context、receiver 或 filter 为 null
     */
    public boolean register(@NonNull Context context,
                            @NonNull BroadcastReceiver receiver,
                            @NonNull IntentFilter filter) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        if (filter == null) {
            throw new IllegalArgumentException("filter must not be null");
        }

        // 检查是否已注册（基于对象身份）
        if (isRegistered(receiver)) {
            YoBluetoothLogger.d(TAG, "接收器 " + receiver + " 已注册——跳过");
            return false;
        }

        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }

        receivers.add(new ReceiverEntry(receiver, appContext));
        YoBluetoothLogger.d(TAG, "接收器已注册: " + receiver);
        return true;
    }

    /**
     * 注销先前注册的 {@link BroadcastReceiver}。
     *
     * <p>若 receiver 未曾注册，则本调用为
     * 空操作（幂等）。系统注销过程中抛出的异常
     * 会被捕获并记录日志。
     *
     * @param context  应用上下文
     * @param receiver 要注销的广播接收器
     * @return 若找到并已注销则返回 {@code true}，
     *         若未注册则返回 {@code false}
     */
    public boolean unregister(@NonNull Context context,
                              @NonNull BroadcastReceiver receiver) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }

        ReceiverEntry found = null;
        for (ReceiverEntry entry : receivers) {
            if (entry.receiver == receiver) {
                found = entry;
                break;
            }
        }

        if (found == null) {
            YoBluetoothLogger.d(TAG, "接收器 " + receiver + " 未注册——跳过注销");
            return false;
        }

        try {
            found.context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // 系统已注销（例如进程终止）
            YoBluetoothLogger.w(TAG, "注销接收器出错: " + receiver, e);
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "注销接收器时发生意外错误: " + receiver, e);
        }

        receivers.remove(found);
        YoBluetoothLogger.d(TAG, "接收器已注销: " + receiver);
        return true;
    }

    /**
     * 注销当前所有已注册的 receiver。
     *
     * <p>调用后注册表为空。通常在清理/释放时调用。
     */
    public void unregisterAll() {
        for (ReceiverEntry entry : receivers) {
            try {
                entry.context.unregisterReceiver(entry.receiver);
            } catch (IllegalArgumentException e) {
                YoBluetoothLogger.w(TAG, "unregisterAll 期间注销接收器出错: "
                        + entry.receiver, e);
            } catch (Exception e) {
                YoBluetoothLogger.e(TAG, "unregisterAll 期间发生意外错误: "
                        + entry.receiver, e);
            }
        }
        receivers.clear();
        YoBluetoothLogger.d(TAG, "全部接收器已注销");
    }

    /**
     * 返回当前已注册 receiver 的数量。
     */
    public int size() {
        return receivers.size();
    }

    /**
     * 检查指定 receiver 实例当前是否已注册。
     */
    private boolean isRegistered(@NonNull BroadcastReceiver receiver) {
        for (ReceiverEntry entry : receivers) {
            if (entry.receiver == receiver) {
                return true;
            }
        }
        return false;
    }

    // ── 内部类型 ──────────────────────────────────────────────────

    private static final class ReceiverEntry {
        final BroadcastReceiver receiver;
        final Context context;

        ReceiverEntry(BroadcastReceiver receiver, Context context) {
            this.receiver = receiver;
            this.context = context;
        }
    }
}
