package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import org.junit.Test;

public final class DownloadUrlResolverTest {

    @Test
    public void remoteHttpUrlPreferredAndTrimmed() {
        RemoteUpdate update = update("  https://remote.example.com/app.apk  ");

        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, "https://init.example.com/app.apk");

        assertEquals("https://remote.example.com/app.apk", resolved.downloadUrl());
        assertTrue(DownloadUrlResolver.hasUsableUrl(resolved));
    }

    @Test
    public void initUrlUsedWhenRemoteEmpty() {
        RemoteUpdate update = update("");

        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, "https://init.example.com/app.apk");

        assertEquals("https://init.example.com/app.apk", resolved.downloadUrl());
    }

    @Test
    public void relativeRemoteFallsBackToInit() {
        RemoteUpdate update = update("/app.apk");

        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, "https://init.example.com/app.apk");

        assertEquals("https://init.example.com/app.apk", resolved.downloadUrl());
    }

    @Test
    public void nonHttpRemoteFallsBackToInit() {
        RemoteUpdate update = update("itms-services://example");

        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, "https://init.example.com/app.apk");

        assertEquals("https://init.example.com/app.apk", resolved.downloadUrl());
    }

    @Test
    public void bothInvalidYieldEmpty() {
        RemoteUpdate update = update("not-a-url");

        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, "ftp://files.example.com/app.apk");

        assertEquals("", resolved.downloadUrl());
        assertFalse(DownloadUrlResolver.hasUsableUrl(resolved));
    }

    private static RemoteUpdate update(String url) {
        return new RemoteUpdate(true, "1.5.0", 150, "desc", url, false);
    }
}
