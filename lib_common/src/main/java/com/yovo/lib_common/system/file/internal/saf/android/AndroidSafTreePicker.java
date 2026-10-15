package com.yovo.lib_common.system.file.internal.saf.android;

import android.app.Activity;
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
 * SAF 目录树选择适配器：只提供契约与授权持久化，startActivityForResult 由宿主 UI 执行。
 */
public final class AndroidSafTreePicker implements SafTreePicker {

    /** 旧式 onActivityResult 请求码。 */
    public static final int REQUEST_SAF_TREE = 0x5AF0;

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
    @Deprecated
    public void pick(@NonNull Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_SAF_TREE);
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

    @Override
    public boolean handlePicked(@NonNull Context context, @Nullable Intent data) {
        return handlePicked(context, data != null ? data.getData() : null);
    }
}
