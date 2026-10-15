package com.yovo.lib_common.system.file.internal.resolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.file.internal.path.PathFileStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public final class PathResolverTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resolve_relativeSubPathStaysUnderRoot() throws IOException {
        File root = folder.newFolder("root");
        PathTarget target = PathTarget.custom(root, "a/b.log");

        FileResult<File> result = PathResolver.resolve(target, rootProvider(root));

        assertTrue(result.success());
        assertEquals(new File(root, "a/b.log").getCanonicalFile(), result.value());
    }

    @Test
    public void resolve_emptySubPathResolvesRoot() throws IOException {
        File root = folder.newFolder("root");

        FileResult<File> result = PathResolver.resolve(
                PathTarget.custom(root, ""), rootProvider(root));

        assertTrue(result.success());
        assertEquals(root.getCanonicalFile(), result.value());
    }

    @Test
    public void resolve_dotDotEscapeRejected() throws IOException {
        File root = folder.newFolder("root");
        File outside = folder.newFolder("outside");
        PathTarget target = PathTarget.custom(root, "../outside/evil.log");

        FileResult<File> result = PathResolver.resolve(target, rootProvider(root));

        assertFalse(result.success());
        assertEquals(FileFailureCode.TARGET_ESCAPE, result.requireFailure().code());
        assertFalse(new File(outside, "evil.log").exists());
    }

    @Test
    public void resolve_absolutePathPassesThrough() throws IOException {
        File file = folder.newFile("absolute.bin");

        FileResult<File> result = PathResolver.resolve(
                PathTarget.absolute(file.getAbsolutePath()), rootProvider(null));

        assertTrue(result.success());
        assertEquals(file.getCanonicalFile(), result.value());
    }

    @Test
    public void resolve_missingRootStillResolvesAndEnsureDirCreates() throws IOException {
        File root = new File(folder.getRoot(), "not-yet");
        PathTarget target = PathTarget.custom(root, "logs");

        FileResult<File> result = PathResolver.resolve(target, rootProvider(root));

        assertTrue(result.success());
        assertTrue(PathFileStore.ensureDir(result.value()).success());
        assertTrue(new File(root, "logs").isDirectory());
    }

    @NonNull
    private static PathRootProvider rootProvider(@Nullable File root) {
        return target -> root;
    }
}
