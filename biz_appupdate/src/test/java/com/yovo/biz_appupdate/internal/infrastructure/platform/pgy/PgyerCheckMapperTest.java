package com.yovo.biz_appupdate.internal.infrastructure.platform.pgy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PgyerCheckMapperTest {

    private static final String API_KEY = "test-api-key";
    private static final String BUILD_KEY = "a1b2c3d4e5f6";
    private static final String INSTALL_URL =
            "https://www.pgyer.com/apiv2/app/install?_api_key=xxx&buildKey=" + BUILD_KEY;

    @Test
    public void officialInstallDownloadUrlUsedAfterTrim() {
        String resolved = PgyerCheckMapper.resolveDownloadUrl(
                "  " + INSTALL_URL + "  ", BUILD_KEY, API_KEY);

        assertEquals(INSTALL_URL, resolved);
    }

    @Test
    public void emptyDownloadUrlConstructsInstallApi() {
        String resolved = PgyerCheckMapper.resolveDownloadUrl("", BUILD_KEY, API_KEY);

        assertEquals(
                "https://www.pgyer.com/apiv2/app/install?_api_key=test-api-key&buildKey=" + BUILD_KEY,
                resolved);
    }

    @Test
    public void shortPageNotUsedWhenBuildKeyPresent() {
        String resolved = PgyerCheckMapper.resolveDownloadUrl(
                "https://www.pgyer.com/abcd", BUILD_KEY, API_KEY);

        assertTrue(resolved.contains("/apiv2/app/install"));
        assertTrue(resolved.contains("buildKey=" + BUILD_KEY));
        assertFalse(resolved.contains("pgyer.com/abcd"));
    }

    @Test
    public void shortPageWithoutBuildKeyRejected() {
        String resolved = PgyerCheckMapper.resolveDownloadUrl(
                "https://www.pgyer.com/abcd", "", API_KEY);

        assertEquals("", resolved);
    }

    @Test
    public void landingPageWithoutBuildKeyRejected() {
        String resolved = PgyerCheckMapper.resolveDownloadUrl(
                "https://www.pgyer.com/app", "", API_KEY);

        assertEquals("", resolved);
    }

    @Test
    public void directHttpApkKeptWhenNoBuildKey() {
        String cdn = "https://cdn.example.com/Auto_Tools_1.5.3.apk";

        String resolved = PgyerCheckMapper.resolveDownloadUrl(cdn, "", API_KEY);

        assertEquals(cdn, resolved);
    }

    @Test
    public void parseFileSizeAcceptsStringAndNumber() {
        assertEquals(12345678L, PgyerCheckMapper.parseFileSize("12345678"));
        assertEquals(12345678L, PgyerCheckMapper.parseFileSize(12345678));
        assertEquals(0L, PgyerCheckMapper.parseFileSize(""));
        assertEquals(0L, PgyerCheckMapper.parseFileSize(null));
    }

    @Test
    public void parseVersionCode() {
        assertEquals(155, PgyerCheckMapper.parseVersionCode("155"));
        assertEquals(0, PgyerCheckMapper.parseVersionCode(""));
        assertEquals(0, PgyerCheckMapper.parseVersionCode(null));
    }
}
