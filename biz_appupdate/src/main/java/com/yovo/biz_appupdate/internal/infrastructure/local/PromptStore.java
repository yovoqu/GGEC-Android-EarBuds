package com.yovo.biz_appupdate.internal.infrastructure.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.PromptProvider;

public final class PromptStore implements PromptProvider {

    private static final String PREF_NAME = "biz_appupdate_prompt";
    private static final String KEY_LAST_VERSION = "last_prompted_version";
    private static final String KEY_IGNORED_VERSIONS = "ignored_versions";

    private final SharedPreferences prefs;

    public PromptStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean shouldPrompt(@Nullable String remoteVersion) {
        if (TextUtils.isEmpty(remoteVersion)) {
            return false;
        }
        return !remoteVersion.equals(prefs.getString(KEY_LAST_VERSION, ""));
    }

    @Override
    public void markPrompted(@NonNull String remoteVersion) {
        prefs.edit().putString(KEY_LAST_VERSION, remoteVersion).apply();
    }

    @Override
    public boolean isIgnored(@NonNull String remoteVersion) {
        return prefs.getStringSet(KEY_IGNORED_VERSIONS, new java.util.HashSet<>())
                .contains(remoteVersion);
    }

    @Override
    public void ignore(@NonNull String remoteVersion) {
        java.util.Set<String> ignored =
                new java.util.HashSet<>(
                        prefs.getStringSet(KEY_IGNORED_VERSIONS, new java.util.HashSet<>()));
        ignored.add(remoteVersion);
        prefs.edit().putStringSet(KEY_IGNORED_VERSIONS, ignored).apply();
    }
}
