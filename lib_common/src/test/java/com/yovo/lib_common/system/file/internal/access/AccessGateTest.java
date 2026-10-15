package com.yovo.lib_common.system.file.internal.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.AccessDenyReason;
import com.yovo.lib_common.system.file.model.AccessMode;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.file.model.UriTarget;
import com.yovo.lib_common.system.permission.model.Permission;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class AccessGateTest {

    @Test
    public void appInternal_alwaysAllowed() {
        assertTrue(AccessGate.checkPath(
                PathTarget.internalFiles("logs"),
                fake(true, true, true, true, true, true, true))
                .isAllowed());
    }

    @Test
    public void absolute_missingStorageDirectDenied() {
        AccessCheckResult result = AccessGate.checkPath(
                PathTarget.absolute("/sdcard/ota"),
                fake(false, true, true, true, true, true, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.MISSING_STORAGE_DIRECT, result.reason());
    }

    @Test
    public void absolute_storageDirectGrantedAllowed() {
        assertTrue(AccessGate.checkPath(
                PathTarget.absolute("/sdcard/ota"),
                fake(true, true, true, true, true, true, true))
                .isAllowed());
    }

    @Test
    public void tree_missingPermissionDenied() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.tree(Uri.parse("content://tree/root")),
                AccessMode.WRITE,
                fake(true, false, false, false, false, false, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.URI_PERMISSION_MISSING, result.reason());
    }

    @Test
    public void tree_permissionLostWhenGrantDisappeared() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.tree(Uri.parse("content://tree/root")),
                AccessMode.READ_WRITE,
                fake(true, false, true, true, false, false, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.URI_PERMISSION_LOST, result.reason());
    }

    @Test
    public void tree_temporaryGrantAllowedWithoutPersistedPermission() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.tree(Uri.parse("content://tree/root")),
                AccessMode.READ_WRITE,
                fake(true, true, false, false, true, true, true));

        assertTrue(result.isAllowed());
    }

    @Test
    public void tree_readOnlyTemporaryGrant_writeDenied() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.tree(Uri.parse("content://tree/root")),
                AccessMode.WRITE,
                fake(true, true, false, false, true, false, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.URI_PERMISSION_MISSING, result.reason());
    }

    @Test
    public void tree_readOnlyTemporaryGrant_readAllowed() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.tree(Uri.parse("content://tree/root")),
                AccessMode.READ,
                fake(true, true, false, false, true, false, true));

        assertTrue(result.isAllowed());
    }

    @Test
    public void document_writeFlagMissingDenied() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.document(Uri.parse("content://doc/a")),
                AccessMode.WRITE,
                fake(true, true, true, true, true, true, false));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.SAF_NOT_WRITABLE, result.reason());
    }

    @Test
    public void document_readTemporaryGrantAllowed() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.document(Uri.parse("content://doc/a")),
                AccessMode.READ,
                fake(true, true, false, false, true, false, true));

        assertTrue(result.isAllowed());
    }

    @Test
    public void document_readNoGrantDenied() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.document(Uri.parse("content://doc/a")),
                AccessMode.READ,
                fake(true, true, false, false, false, false, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.URI_PERMISSION_MISSING, result.reason());
    }

    @Test
    public void document_writeNoGrantDenied() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.document(Uri.parse("content://doc/a")),
                AccessMode.WRITE,
                fake(true, true, false, false, false, false, true));

        assertFalse(result.isAllowed());
        assertEquals(AccessDenyReason.URI_PERMISSION_MISSING, result.reason());
    }

    @Test
    public void document_writeTemporaryGrantAllowed() {
        AccessCheckResult result = AccessGate.checkUri(
                UriTarget.document(Uri.parse("content://doc/a")),
                AccessMode.WRITE,
                fake(true, true, false, false, false, true, true));

        assertTrue(result.isAllowed());
    }

    @NonNull
    private static AccessEnvironment fake(boolean storageGranted,
                                          boolean treeAccessible,
                                          boolean persistedRead,
                                          boolean persistedWrite,
                                          boolean canOpenRead,
                                          boolean canOpenWrite,
                                          boolean documentSupportsWrite) {
        return new AccessEnvironment() {
            @Override
            public boolean isStorageDirectGranted() {
                return storageGranted;
            }

            @Override
            @NonNull
            public List<Permission> missingStorageDirectPermissions() {
                return storageGranted
                        ? Collections.emptyList()
                        : Collections.singletonList(Permission.MANAGE_EXTERNAL_STORAGE);
            }

            @Override
            @Nullable
            public File externalRoot(@NonNull PathTarget target) {
                return new File(".");
            }

            @Override
            public boolean hasPersistedReadPermission(@NonNull Uri uri) {
                return persistedRead;
            }

            @Override
            public boolean hasPersistedWritePermission(@NonNull Uri uri) {
                return persistedWrite;
            }

            @Override
            public boolean isDocumentUri(@NonNull Uri uri) {
                return true;
            }

            @Override
            public boolean documentSupportsWrite(@NonNull Uri uri) {
                return documentSupportsWrite;
            }

            @Override
            public boolean canOpenDocument(@NonNull Uri uri, boolean write) {
                return write ? canOpenWrite : canOpenRead;
            }

            @Override
            public boolean isTreeDirectory(@NonNull Uri treeUri) {
                return treeAccessible;
            }
        };
    }
}
