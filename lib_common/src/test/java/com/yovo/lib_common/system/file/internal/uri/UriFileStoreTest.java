package com.yovo.lib_common.system.file.internal.uri;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.UriFileInfo;
import com.yovo.lib_common.system.file.model.UriTarget;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class UriFileStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void inspect_fileUriUsesSafeDisplayName() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = write("firmware.bin", "firmware");
        Uri uri = Uri.fromFile(source);

        FileResult<UriFileInfo> info =
                UriFileStore.inspect(context, UriTarget.document(uri));

        assertTrue(info.success());
        assertEquals("firmware.bin", info.value().displayName());
    }

    @Test
    public void copyInto_importsFileWithUniqueName() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = write("firmware.bin", "firmware");
        File destinationDir = folder.newFolder("destination");

        FileResult<File> copied = UriFileStore.copyInto(
                context,
                UriTarget.document(Uri.fromFile(source)),
                destinationDir,
                null,
                UriFileStore.NO_SIZE_LIMIT,
                ConflictPolicy.RENAME_UNIQUE);

        assertTrue(copied.success());
        assertEquals(source.length(), copied.value().length());
        assertTrue(copied.value().isFile());
    }

    @Test
    public void copyTo_enforcesActualByteLimitAndDeletesPartialFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = write("large.bin", "0123456789");
        File destination = new File(folder.newFolder("limited"), "large.bin");

        FileResult<Long> copied = UriFileStore.copyTo(
                context,
                UriTarget.document(Uri.fromFile(source)),
                destination,
                5L,
                ConflictPolicy.OVERWRITE);

        assertFalse(copied.success());
        assertEquals(FileFailureCode.SIZE_LIMIT_EXCEEDED, copied.requireFailure().code());
        assertFalse(destination.exists());
    }

    private File write(String name, String content) throws Exception {
        File file = folder.newFile(name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
