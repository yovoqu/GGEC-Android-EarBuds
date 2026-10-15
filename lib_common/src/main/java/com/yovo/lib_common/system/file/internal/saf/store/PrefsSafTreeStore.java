package com.yovo.lib_common.system.file.internal.saf.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.SafTreeStore;

/**
 * SAF 目录树授权持久化（SharedPreferences 实现）。
 */
public final class PrefsSafTreeStore implements SafTreeStore {

    private static final String PREF_NAME = "lib_common_saf";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_DISMISSED = "picker_dismissed";

    private final SharedPreferences prefs;

    public PrefsSafTreeStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void save(@NonNull Uri treeUri) {
        prefs.edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .remove(KEY_DISMISSED)
                .apply();
    }

    @Override
    @Nullable
    public Uri load() {
        String value = prefs.getString(KEY_TREE_URI, "");
        return TextUtils.isEmpty(value) ? null : Uri.parse(value);
    }

    @Override
    public boolean has() {
        return load() != null;
    }

    @Override
    public void markDismissed() {
        prefs.edit().putBoolean(KEY_DISMISSED, true).apply();
    }

    @Override
    public void clearDismissed() {
        prefs.edit().remove(KEY_DISMISSED).apply();
    }

    @Override
    public boolean isDismissed() {
        return prefs.getBoolean(KEY_DISMISSED, false);
    }
}
