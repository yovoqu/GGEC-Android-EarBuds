package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 平台命名（PLT-03/04）：默认名＝模板名，重名加最小未用序号；改名 1–32 字、不得与其他平台重名。
 */
public final class AiPlatformNamePolicy {

    public static final int MAX_LENGTH = 32;

    public enum Error {
        EMPTY,
        TOO_LONG,
        DUPLICATE
    }

    private AiPlatformNamePolicy() {
    }

    public static String defaultName(String base, Collection<String> existing) {
        Set<String> taken = new HashSet<>(existing);
        if (!taken.contains(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + " " + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** @param others 其他平台的名称（不含自己） */
    @Nullable
    public static Error validate(@Nullable String raw, Collection<String> others) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            return Error.EMPTY;
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            return Error.TOO_LONG;
        }
        if (others.contains(name)) {
            return Error.DUPLICATE;
        }
        return null;
    }
}
