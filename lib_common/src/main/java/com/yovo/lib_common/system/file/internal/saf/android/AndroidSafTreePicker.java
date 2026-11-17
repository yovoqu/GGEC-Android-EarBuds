package com.yovo.lib_common.system.file.internal.saf.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.SafTreePicker;
import com.yovo.lib_common.system.file.model.SafTreeStore;

/**
 * SAF 目录树选择适配器：注册系统选择器并把授权持久化到 {@link SafTreeStore}。
 */
public final class AndroidSafTreePicker implements SafTreePicker {

    private final SafTreeStore safTreeStore;

    public AndroidSafTreePicker(@NonNull SafTreeStore safTreeStore) {
        this.safTreeStore = safTreeStore;
    }

    @Override
    @NonNull
    public ActivityResultLauncher<Uri> register(@NonNull ComponentActivity activity,
                                                @NonNull PickedCallback callback) {
        return activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                treeUri -> callback.onResult(handlePicked(activity, treeUri)));
    }

    @Override
    public boolean handlePicked(@NonNull Context context, @Nullable Uri treeUri) {
        if (treeUri == null) {
            safTreeStore.markDismissed();
            return false;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException e) {
            safTreeStore.markDismissed();
            return false;
        }
        safTreeStore.save(treeUri);
        return true;
    }
}
