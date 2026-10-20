package com.ggec.earbuds.data.ai.store;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;

import java.util.function.Supplier;

/**
 * 存储层抛出的 {@link AiStorageException} 是数据层内部的事，仓库对外只说领域的存储失败，
 * 调用方认识 {@link AiFailure} 就够了，也就能把"磁盘满了""密钥库坏了"变成一句提示，而不是崩溃。
 */
public final class StorageGuard {

    private StorageGuard() {
    }

    public static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (AiStorageException failure) {
            throw new AiFailure(AiFailureKind.STORAGE, failure.getMessage(), failure);
        }
    }

    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
