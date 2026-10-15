package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;

import com.yovo.biz_appupdate.internal.interactor.ApkRetentionDecider.RetentionPlan;
import com.yovo.biz_appupdate.internal.model.CurrentAppVersion;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class ApkRetentionDeciderTest {

    private static final String PACKAGE = "com.ggec.auto_tools";
    private static final String SIG = "sig";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void singleLatest_keepsNewestAndRemovesCurrent() throws Exception {
        List<LocalApk> candidates = Arrays.asList(
                apk("current", 149, "1.4.7"),
                apk("old", 140, "1.4.0"),
                apk("new", 150, "1.4.8"));

        RetentionPlan plan = ApkRetentionDecider.decide(
                candidates,
                new CurrentAppVersion("1.4.7", 149),
                PACKAGE,
                SIG,
                StoragePolicy.SINGLE_LATEST);

        assertEquals(1, plan.keep().size());
        assertEquals(150, plan.keep().get(0).versionCode());
        assertEquals(2, plan.remove().size());
    }

    @Test
    public void multiVersion_keepsAllNonCurrent() throws Exception {
        List<LocalApk> candidates = Arrays.asList(
                apk("current", 149, "1.4.7"),
                apk("old", 140, "1.4.0"),
                apk("new", 150, "1.4.8"));

        RetentionPlan plan = ApkRetentionDecider.decide(
                candidates,
                new CurrentAppVersion("1.4.7", 149),
                PACKAGE,
                SIG,
                StoragePolicy.MULTI_VERSION);

        assertEquals(2, plan.keep().size());
        assertEquals(1, plan.remove().size());
        assertEquals(149, plan.remove().get(0).versionCode());
    }

    @Test
    public void invalidPackageOrSignature_isRemoved() throws Exception {
        LocalApk invalid = new LocalApk(
                new File(tempFolder.getRoot(), "Auto_Tools_1.4.8_150.apk"),
                1L,
                "1.4.8",
                150,
                "other-sig",
                "other.pkg");

        RetentionPlan plan = ApkRetentionDecider.decide(
                Arrays.asList(invalid),
                new CurrentAppVersion("1.4.7", 149),
                PACKAGE,
                SIG,
                StoragePolicy.MULTI_VERSION);

        assertEquals(0, plan.keep().size());
        assertEquals(1, plan.remove().size());
    }

    private LocalApk apk(String name, int versionCode, String versionName) throws Exception {
        File file = new File(tempFolder.getRoot(), name + ".apk");
        if (!file.createNewFile() && !file.exists()) {
            throw new IllegalStateException("cannot create test apk");
        }
        return new LocalApk(file, file.lastModified(), versionName, versionCode, SIG, PACKAGE);
    }
}
