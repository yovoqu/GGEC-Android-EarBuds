package com.yovo.biz_appupdate.internal.model;

import com.yovo.biz_appupdate.provider.lib_common.DownloadDirPermissionPolicy;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.yovo.lib_common.system.permission.model.Permission;

import org.junit.Test;

public final class DownloadDirPermissionPolicyTest {

    @Test
    public void android10_requiresWriteExternalStorage() {
        assertEquals(Permission.WRITE_EXTERNAL_STORAGE,
                DownloadDirPermissionPolicy.requiredFor(
                        29, DownloadDirSpec.Type.ABSOLUTE));
    }

    @Test
    public void android11To17_requiresManageExternalStorage() {
        for (int sdk = 30; sdk <= 37; sdk++) {
            assertEquals("SDK " + sdk + " should require MANAGE_EXTERNAL_STORAGE",
                    Permission.MANAGE_EXTERNAL_STORAGE,
                    DownloadDirPermissionPolicy.requiredFor(
                            sdk, DownloadDirSpec.Type.ABSOLUTE));
        }
    }

    @Test
    public void android9OrLower_requiresNothing() {
        for (int sdk = 24; sdk <= 28; sdk++) {
            assertNull(DownloadDirPermissionPolicy.requiredFor(
                    sdk, DownloadDirSpec.Type.ABSOLUTE));
        }
    }

    @Test
    public void appPrivateDir_neverRequiresPermission() {
        for (int sdk = 29; sdk <= 37; sdk++) {
            assertNull(DownloadDirPermissionPolicy.requiredFor(
                    sdk, DownloadDirSpec.Type.APP_PRIVATE));
            assertNull(DownloadDirPermissionPolicy.requiredFor(
                    sdk, DownloadDirSpec.Type.SAF));
        }
    }
}
