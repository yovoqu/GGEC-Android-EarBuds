package com.yovo.lib_common.download.model;

import static org.junit.Assert.assertEquals;

import com.yovo.lib_common.download.YoDownload;

import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BreakpointInfoTest {

    @Test
    public void fullConstructor_preservesRequestContract() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer x");
        File target = new File("/data/out.apk");
        BreakpointInfo info = new BreakpointInfo(
                "id", "https://example.com/a.apk", target,
                new File("/data/out.apk.download"), 100L, "etag", "lm",
                YoDownload.State.PAUSED, 1L, 2L,
                headers, 3, YoDownload.NetworkType.WIFI,
                YoDownload.Priority.HIGH, "title", "application/vnd.android.package-archive");

        assertEquals("Bearer x", info.headers().get("Authorization"));
        assertEquals(3, info.blockCount());
        assertEquals(YoDownload.NetworkType.WIFI, info.networkType());
        assertEquals(YoDownload.Priority.HIGH, info.priority());
        assertEquals("title", info.title());
        assertEquals("application/vnd.android.package-archive", info.mimeType());
        assertEquals(Collections.singleton("Authorization"), info.headers().keySet());
    }
}
