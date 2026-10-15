package com.yovo.lib_common.system.keepalive.internal.service;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveContracts;

public final class KeepAliveIntentParser {

    private KeepAliveIntentParser() {}

    @NonNull
    public static String parseAction(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return KeepAliveContracts.ACTION_START;
        }
        return intent.getAction();
    }

    @Nullable
    public static String parseSessionId(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        return intent.getStringExtra(KeepAliveContracts.EXTRA_SESSION_ID);
    }
}
