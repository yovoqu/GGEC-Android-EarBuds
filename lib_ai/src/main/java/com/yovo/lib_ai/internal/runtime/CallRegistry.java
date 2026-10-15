package com.yovo.lib_ai.internal.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CallRegistry {

    private final Set<CancelToken> tokens = ConcurrentHashMap.newKeySet();

    public void register(CancelToken token) {
        tokens.add(token);
    }

    public void unregister(CancelToken token) {
        tokens.remove(token);
    }

    public void cancelAll() {
        for (CancelToken token : tokens) {
            token.cancel();
        }
    }
}
