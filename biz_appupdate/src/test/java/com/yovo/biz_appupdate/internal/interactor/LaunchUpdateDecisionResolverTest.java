package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.yovo.biz_appupdate.internal.model.DownloadedApk;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import org.junit.Test;

import java.io.File;

public final class LaunchUpdateDecisionResolverTest {

    private static final long DISCOVERED_AT = 1_700_000_000_000L;

    private final RemoteUpdate remote = new RemoteUpdate(
            true, "1.5.0", 150, "desc", "https://example.com/app.apk", false);

    @Test
    public void noNewVersion_returnsNone() {
        RemoteUpdate noUpdate = new RemoteUpdate(
                false, "1.4.7", 149, "", "https://example.com/app.apk", false);
        LocalApk local = local("Auto_Tools_1.4.7.apk", DISCOVERED_AT + 1000, "1.4.7");

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(noUpdate, null, local, true);

        assertEquals(LaunchDecision.Action.NONE, decision.action());
        assertNull(decision.localApk());
    }

    @Test
    public void matchingFilenameVersion_installsDirect() {
        LocalApk local = local("Auto_Tools_1.5.0.apk", DISCOVERED_AT - 10_000, "1.5.0");

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, null, local, true);

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
        assertNotNull(decision.localApk());
        assertEquals(local.file(), decision.localApk().file());
    }

    @Test
    public void matchingRealVersionCode_installsDirect() {
        LocalApk local = local(
                "Auto_Tools_fake.apk", DISCOVERED_AT - 10_000, null, 150, "abc");

        LaunchDecision decision = LaunchUpdateDecisionResolver.resolve(
                remote, null, local, true, "abc");

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    @Test
    public void signatureMismatch_blocksDirectInstall() {
        LocalApk local = local(
                "Auto_Tools_1.5.0.apk", DISCOVERED_AT - 10_000, "1.5.0", 150, "attacker");

        LaunchDecision decision = LaunchUpdateDecisionResolver.resolve(
                remote, null, local, true, "current-app");

        assertEquals(LaunchDecision.Action.SHOW_UPDATE_DIALOG, decision.action());
    }

    @Test
    public void signatureMatch_allowsDirectInstall() {
        LocalApk local = local(
                "Auto_Tools_1.5.0.apk", DISCOVERED_AT - 10_000, "1.5.0", 150, "current-app");

        LaunchDecision decision = LaunchUpdateDecisionResolver.resolve(
                remote, null, local, true, "current-app");

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    @Test
    public void noExpectedSignature_stillAllowsDirectInstall() {
        LocalApk local = local("Auto_Tools_1.5.0.apk", DISCOVERED_AT - 10_000, "1.5.0");

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, null, local, true);

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    @Test
    public void matchingDownloadRecord_installsDirect() {
        File file = new File("C:/fake/AppUpdate/Auto_Tools_1.5.0.apk");
        LocalApk local = new LocalApk(file, DISCOVERED_AT - 10_000, null);
        UpdateMeta meta = new UpdateMeta(
                remote.version(), remote.versionCode(), "", remote.downloadUrl(), false,
                DISCOVERED_AT,
                new DownloadedApk(file.getAbsolutePath(), "1.5.0", 150, DISCOVERED_AT + 5000));

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, meta, local, true);

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    @Test
    public void newerModificationTime_installsDirect() {
        LocalApk local = local("Auto_Tools_unknown.apk", DISCOVERED_AT + 60_000, null);
        UpdateMeta meta = new UpdateMeta(
                remote.version(), remote.versionCode(), "", remote.downloadUrl(), false,
                DISCOVERED_AT, null);

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, meta, local, true);

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    @Test
    public void noMatchingLocal_showsUpdateDialog() {
        LocalApk local = local("Auto_Tools_1.4.0.apk", DISCOVERED_AT - 100_000, "1.4.0");

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, null, local, true);

        assertEquals(LaunchDecision.Action.SHOW_UPDATE_DIALOG, decision.action());
    }

    @Test
    public void autoInstallDisabled_alwaysShowsUpdateDialog() {
        LocalApk local = local("Auto_Tools_1.5.0.apk", DISCOVERED_AT + 60_000, "1.5.0");

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(remote, null, local, false);

        assertEquals(LaunchDecision.Action.SHOW_UPDATE_DIALOG, decision.action());
    }

    @Test
    public void offlineFallbackMeta_withMatchingLocal_installsDirect() {
        LocalApk local = local("Auto_Tools_1.5.0.apk", DISCOVERED_AT - 10_000, "1.5.0");
        UpdateMeta meta = new UpdateMeta(
                remote.version(), remote.versionCode(), "", remote.downloadUrl(), false,
                DISCOVERED_AT, null);

        LaunchDecision decision =
                LaunchUpdateDecisionResolver.resolve(meta.toRemoteUpdate(), meta, local, true);

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
    }

    private static LocalApk local(String name, long lastModified, String version) {
        return local(name, lastModified, version, 0, null);
    }

    private static LocalApk local(String name,
                                  long lastModified,
                                  String version,
                                  int versionCode,
                                  String signatureHash) {
        return new LocalApk(new File(name), lastModified, version, versionCode, signatureHash);
    }
}
