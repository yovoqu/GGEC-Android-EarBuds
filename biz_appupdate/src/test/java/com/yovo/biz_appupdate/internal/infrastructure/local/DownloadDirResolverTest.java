package com.yovo.biz_appupdate.internal.infrastructure.local;

import static org.junit.Assert.assertEquals;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public final class DownloadDirResolverTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void appPrivate_resolvesUnderExternalFilesDir() {
        DownloadDirSpec spec = DownloadDirSpec.appPrivate("AppUpdate");
        File resolved = DownloadDirResolver.resolve(
                spec, new File("/data/user/0/pkg/files"), new File("/data/files"));

        assertEquals(new File("/data/user/0/pkg/files/AppUpdate"), resolved);
    }

    @Test
    public void appPrivate_fallsBackToInternalFilesDir() {
        DownloadDirSpec spec = DownloadDirSpec.appPrivate("AppUpdate");
        File resolved = DownloadDirResolver.resolve(
                spec, null, new File("/data/files"));

        assertEquals(new File("/data/files/AppUpdate"), resolved);
    }

    @Test
    public void absolute_keepsAbsoluteDir() throws Exception {
        File dir = new File(tempFolder.getRoot(), "Download/Auto_Tools").getAbsoluteFile();
        DownloadDirSpec spec = DownloadDirSpec.absolute(dir);

        assertEquals(dir, DownloadDirResolver.resolve(
                spec, new File("/x/files"), new File("/y/files")));
    }

    @Test
    public void saf_usesInternalCacheDir() {
        DownloadDirSpec spec = DownloadDirSpec.saf();

        assertEquals(new File("/data/files/AppUpdate_saf_cache"),
                DownloadDirResolver.resolve(
                        spec, new File("/x/files"), new File("/data/files")));
    }
}
