package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description ACL 链路状态变化观察者，在设备快照更新时接收回调。
 */

public interface YoCbtAclLinkObserver {
    void onAclLinkChanged(@NonNull YoCbtDeviceSnapshot snapshot);
}
