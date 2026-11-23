package com.yovo.biz_ggec_wy.internal.record;

import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.File;

/**
 * {@link WyRecordPathResolver} 的命名规则单测：型号 + 类型 + 时间戳。
 */
public final class WyRecordPathResolverTest {

    @Test
    public void streamFileCarriesModelTypeAndTimestamp() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"),
                "wy_naming_" + System.currentTimeMillis());
        tempDir.mkdirs();
        try {
            WyRecordPathResolver resolver = new WyRecordPathResolver(null, () -> "WY01") {
                @NonNull
                @Override
                public File getStreamDir() {
                    return tempDir;
                }
            };
            File file = resolver.generateDefaultStreamFile();
            assertTrue(file.getName(),
                    file.getName().matches("WY01_stream_\\d{8}_\\d{6}\\.pcm"));
            assertTrue(file.getParentFile().equals(tempDir));
        } finally {
            File[] remaining = tempDir.listFiles();
            if (remaining != null) {
                for (File f : remaining) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }
}
