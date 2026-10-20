package com.ggec.earbuds.testing.ai;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.store.SecretStore;

import java.util.HashMap;
import java.util.Map;

public final class MemorySecretStore implements SecretStore {

    public final Map<String, String> secrets = new HashMap<>();

    @Nullable
    @Override
    public String get(String alias) {
        return secrets.get(alias);
    }

    @Override
    public void put(String alias, String secret) {
        secrets.put(alias, secret);
    }

    @Override
    public void remove(String alias) {
        secrets.remove(alias);
    }
}
