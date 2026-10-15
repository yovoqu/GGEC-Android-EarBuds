package com.yovo.lib_common.system.receive;

import com.yovo.lib_common.system.receive.model.IncomingOpen;
import com.yovo.lib_common.system.receive.model.IncomingShare;

import com.yovo.lib_common.system.receive.internal.ReceiveCore;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * 入站内容解析对外门面。
 */
public final class YoReceive {

    private YoReceive() {
    }

    @Nullable
    public static IncomingShare readShare(@Nullable Intent intent) {
        return ReceiveCore.readShare(intent);
    }

    @Nullable
    public static IncomingOpen readOpen(@Nullable Intent intent) {
        return ReceiveCore.readOpen(intent);
    }
}
