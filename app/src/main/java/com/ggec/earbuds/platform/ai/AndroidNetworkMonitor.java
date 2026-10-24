package com.ggec.earbuds.platform.ai;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiNetworkMonitor;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 用默认网络回调判断在线：默认网络存在且带 INTERNET 能力即在线。只在两态切换时通知。
 *
 * <p>不看 VALIDATED：部分代理/内网环境永远不会通过系统的连通性检测，按它判会把能用的网络当离线，
 * 挡住用户的请求。真不通时请求自己会失败并给出"网络"分类。</p>
 */
public final class AndroidNetworkMonitor implements AiNetworkMonitor {

    @Nullable
    private final ConnectivityManager connectivity;
    private final CopyOnWriteArrayList<Consumer<Boolean>> observers = new CopyOnWriteArrayList<>();
    private volatile boolean online;

    public AndroidNetworkMonitor(@NonNull Context context) {
        connectivity = context.getSystemService(ConnectivityManager.class);
        online = connectivity == null || currentlyOnline(connectivity);
        if (connectivity == null) {
            return;
        }
        try {
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                    update(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    update(false);
                }

                @Override
                public void onUnavailable() {
                    update(false);
                }
            });
        } catch (RuntimeException unsupported) {
            // 拿不到回调（权限被裁、厂商限制）时按在线处理，交给请求本身去失败。
            online = true;
        }
    }

    @Override
    public boolean online() {
        return online;
    }

    @Override
    public Subscription observe(Consumer<Boolean> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    private void update(boolean next) {
        if (online == next) {
            return;
        }
        online = next;
        for (Consumer<Boolean> observer : observers) {
            try {
                observer.accept(next);
            } catch (RuntimeException ignored) {
                // 一个观察者出错不影响其他观察者。
            }
        }
    }

    private static boolean currentlyOnline(ConnectivityManager connectivity) {
        try {
            Network network = connectivity.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException unknown) {
            return true;
        }
    }
}
