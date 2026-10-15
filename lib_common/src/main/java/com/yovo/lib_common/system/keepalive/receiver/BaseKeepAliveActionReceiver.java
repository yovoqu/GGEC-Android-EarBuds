package com.yovo.lib_common.system.keepalive.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.yovo.lib_common.system.keepalive.model.KeepAliveContracts;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveActionDispatcher;
import com.yovo.lib_common.system.keepalive.internal.service.KeepAliveIntentParser;

public class BaseKeepAliveActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (!KeepAliveContracts.ACTION_USER_STOP.equals(intent.getAction())) {
            return;
        }
        String sessionId = KeepAliveIntentParser.parseSessionId(intent);
        if (sessionId == null) {
            return;
        }
        KeepAliveActionDispatcher.dispatchUserStop(sessionId);
    }
}
