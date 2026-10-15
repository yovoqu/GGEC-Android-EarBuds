package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * ACL 链路观察者。
 */
public interface CbtAclLinkObserver {

    default void onAclLinkChanged(@NonNull CbtDeviceSnapshot snapshot) {
    }
}
