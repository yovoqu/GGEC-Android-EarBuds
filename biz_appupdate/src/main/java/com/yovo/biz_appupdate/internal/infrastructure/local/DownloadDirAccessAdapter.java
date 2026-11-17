package com.yovo.biz_appupdate.internal.infrastructure.local;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.DownloadDirPermissionPort;
import com.yovo.biz_appupdate.internal.contract.SafTreeStorePort;
import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

/**
 * 下载目录可访问性判定。
 */
public final class DownloadDirAccessAdapter implements DownloadDirAccessPort {

    private final Context appContext;
    private final DownloadDirSpec dirSpec;
    private final DownloadDirPermissionPort permissionProvider;
    private final SafTreeStorePort safProvider;

    public DownloadDirAccessAdapter(@NonNull Context context,
                                    @NonNull DownloadDirSpec dirSpec,
                                    @NonNull DownloadDirPermissionPort permissionProvider,
                                    @NonNull SafTreeStorePort safProvider) {
        this.appContext = context.getApplicationContext();
        this.dirSpec = dirSpec;
        this.permissionProvider = permissionProvider;
        this.safProvider = safProvider;
    }

    @Override
    public boolean isAccessible() {
        return switch (dirSpec.type()) {
            case APP_PRIVATE -> true;
            case ABSOLUTE -> permissionProvider.isGranted(dirSpec.type());
            case SAF -> {
                Uri treeUri = dirSpec.safTreeUri() != null
                        ? dirSpec.safTreeUri()
                        : safProvider.loadTreeUri();
                yield safProvider.isTreeAccessible(treeUri);
            }
        };
    }
}
