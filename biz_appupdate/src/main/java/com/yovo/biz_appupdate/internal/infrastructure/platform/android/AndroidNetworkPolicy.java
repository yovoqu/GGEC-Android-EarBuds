package com.yovo.biz_appupdate.internal.infrastructure.platform.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;

/**
 * Android 网络策略实现（API 24+ 走 NetworkCapabilities）。
 */
public final class AndroidNetworkPolicy implements NetworkPolicy {

    private final Context appContext;

    public AndroidNetworkPolicy(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public boolean isWifiConnected() {
        ConnectivityManager manager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
