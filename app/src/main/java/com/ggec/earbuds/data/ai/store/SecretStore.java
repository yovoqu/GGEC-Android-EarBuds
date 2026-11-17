package com.ggec.earbuds.data.ai.store;

import androidx.annotation.Nullable;

public interface SecretStore {

    /**
     * 取不到、或者解不开，一律返回空：丢失的密钥让用户重新输入，不能让应用崩溃。
     * 文档本身读不出来（损坏、版本不符）不在此列，必须向上抛。
     */
    @Nullable
    String get(String alias);

    void put(String alias, String secret);

    void remove(String alias);
}
