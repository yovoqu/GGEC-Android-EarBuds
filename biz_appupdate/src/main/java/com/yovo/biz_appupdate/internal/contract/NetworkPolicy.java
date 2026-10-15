package com.yovo.biz_appupdate.internal.contract;

/**
 * 网络策略端口：WiFi-only 等约束统一在此判定。
 */
public interface NetworkPolicy {

    boolean isWifiConnected();
}
