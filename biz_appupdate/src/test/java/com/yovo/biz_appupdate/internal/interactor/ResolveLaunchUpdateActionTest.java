package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkStoragePort;
import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;
import com.yovo.biz_appupdate.internal.contract.PendingDownloadResumer;
import com.yovo.biz_appupdate.internal.contract.PromptProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.model.ApkNamingSpec;
import com.yovo.biz_appupdate.internal.model.DownloadedApk;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ResolveLaunchUpdateActionTest {

    private static final String VERSION = "1.4.8";
    private static final String PACKAGE = "com.ggec.auto_tools";
    private static final String SIG = "expected-signature";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final FakeApkStorage apkStorage = new FakeApkStorage();
    private final FakeCheckProvider checkProvider = new FakeCheckProvider();
    private final FakeVersionProvider versionProvider = new FakeVersionProvider();
    private final FakeMetaStore metaStore = new FakeMetaStore();
    private final FakePromptProvider promptProvider = new FakePromptProvider();
    private final FakeNetworkPolicy networkPolicy = new FakeNetworkPolicy(true);
    private final FakeResumer resumer = new FakeResumer();

    private final UpdatePolicy policy = new UpdatePolicy(
            "https://example.com/app.apk",
            false,
            true,
            "Auto_Tools",
            1000L,
            5000L);

    @Test
    public void dirNotAccessible_returnsNoneWithoutChecking() {
        FakeDownloadDirAccess access = new FakeDownloadDirAccess(false);
        ResolveLaunchUpdateAction action = createAction(access);

        LaunchDecision decision = run(action);

        assertEquals(LaunchDecision.Action.NEED_DOWNLOAD_DIR, decision.action());
        assertEquals(0, checkProvider.calls);
    }

    @Test
    public void remoteNewAndLocalMatches_returnsInstallDirect() throws Exception {
        apkStorage.locals = Collections.singletonList(localApk(VERSION));
        checkProvider.result = RemoteUpdateFactory.newVersion(VERSION);

        LaunchDecision decision = run(createAction(new FakeDownloadDirAccess(true)));

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
        assertNotNull(decision.localApk());
        assertEquals(VERSION, decision.update().version());
        assertTrue(apkStorage.cleanedUp);
    }

    @Test
    public void remoteNoNewVersion_returnsNone() {
        checkProvider.result = new RemoteUpdate(
                false, VERSION, 150, "desc", "https://example.com/app.apk", false);

        LaunchDecision decision = run(createAction(new FakeDownloadDirAccess(true)));

        assertEquals(LaunchDecision.Action.NONE, decision.action());
        assertTrue(metaStore.cleared);
    }

    @Test
    public void remoteIgnored_returnsNone() {
        checkProvider.result = RemoteUpdateFactory.newVersion(VERSION);
        promptProvider.ignored = true;

        LaunchDecision decision = run(createAction(new FakeDownloadDirAccess(true)));

        assertEquals(LaunchDecision.Action.NONE, decision.action());
    }

    @Test
    public void offlineWithMetaAndLocal_returnsInstallDirect() throws Exception {
        apkStorage.locals = Collections.singletonList(localApk(VERSION));
        metaStore.meta = new UpdateMeta(
                VERSION, 150, "desc", "https://example.com/app.apk",
                false, System.currentTimeMillis(), null);
        networkPolicy.wifi = false;
        UpdatePolicy offlinePolicy = new UpdatePolicy(
                "https://example.com/app.apk",
                true,
                true,
                "Auto_Tools",
                1000L,
                5000L);

        LaunchDecision decision = run(
                createAction(new FakeDownloadDirAccess(true), offlinePolicy));

        assertEquals(LaunchDecision.Action.INSTALL_DIRECT, decision.action());
        assertEquals(0, checkProvider.calls);
    }

    private ResolveLaunchUpdateAction createAction(FakeDownloadDirAccess access) {
        return createAction(access, policy);
    }

    private ResolveLaunchUpdateAction createAction(FakeDownloadDirAccess access,
                                                   UpdatePolicy updatePolicy) {
        return new ResolveLaunchUpdateAction(
                checkProvider,
                versionProvider,
                metaStore,
                apkStorage,
                updatePolicy,
                networkPolicy,
                promptProvider,
                resumer,
                access);
    }

    private LaunchDecision run(ResolveLaunchUpdateAction action) {
        AtomicReference<LaunchDecision> holder = new AtomicReference<>();
        action.run(holder::set);
        return holder.get();
    }

    private LocalApk localApk(String version) throws Exception {
        File file = new File(tempFolder.getRoot(), "Auto_Tools_" + version + "_150.apk");
        assertTrue(file.createNewFile());
        return new LocalApk(file, file.lastModified(), version, 150, SIG, PACKAGE);
    }

    private static final class RemoteUpdateFactory {
        private static RemoteUpdate newVersion(String version) {
            return new RemoteUpdate(
                    true, version, 150, "desc", "https://example.com/app.apk", false);
        }
    }

    private static final class FakeDownloadDirAccess implements DownloadDirAccessPort {
        private final boolean accessible;

        private FakeDownloadDirAccess(boolean accessible) {
            this.accessible = accessible;
        }

        @Override
        public boolean isAccessible() {
            return accessible;
        }
    }

    private static final class FakeApkStorage implements ApkStoragePort {
        @NonNull
        private List<LocalApk> locals = new ArrayList<>();
        private boolean cleanedUp;

        @NonNull
        @Override
        public List<LocalApk> listApks() {
            return locals;
        }

        @Override
        public boolean deleteApk(@NonNull LocalApk apk) {
            locals.remove(apk);
            return true;
        }

        @Override
        public void deleteOwnedFilesExcept(@NonNull Collection<File> keepFiles) {
            cleanedUp = true;
        }

        @NonNull
        @Override
        public File rootDir() {
            return new File("/tmp/appupdate");
        }

        @NonNull
        @Override
        public String fileName(@NonNull String versionName, int versionCode) {
            return new ApkNamingSpec("Auto_Tools").fileName(versionName, versionCode);
        }

        @NonNull
        @Override
        public String currentSignatureHash() {
            return SIG;
        }

        @NonNull
        @Override
        public String currentPackageName() {
            return PACKAGE;
        }
    }

    private static final class FakeCheckProvider implements UpdateCheckProvider {
        @Nullable
        private RemoteUpdate result;
        private int calls;

        @Override
        public void check(@NonNull String currentVersion, @NonNull Callback callback) {
            calls++;
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError("offline");
            }
        }
    }

    private static final class FakeVersionProvider implements VersionProvider {
        @NonNull
        @Override
        public String currentVersionName() {
            return "1.4.7";
        }

        @Override
        public int currentVersionCode() {
            return 149;
        }
    }

    private static final class FakeMetaStore implements UpdateMetaStore {
        @Nullable
        private UpdateMeta meta;
        private boolean cleared;

        @Override
        public void record(@NonNull RemoteUpdate update) {
            meta = new UpdateMeta(
                    update.version(), update.versionCode(), update.description(),
                    update.downloadUrl(), update.forceUpdate(),
                    System.currentTimeMillis(), null);
        }

        @Override
        public void recordDownloadedApk(@NonNull RemoteUpdate update, @NonNull File apkFile) {
            meta = new UpdateMeta(
                    update.version(), update.versionCode(), update.description(),
                    update.downloadUrl(), update.forceUpdate(),
                    System.currentTimeMillis(),
                    new DownloadedApk(apkFile.getAbsolutePath(), update.version(),
                            update.versionCode(), System.currentTimeMillis()));
        }

        @Nullable
        @Override
        public UpdateMeta snapshot() {
            return meta;
        }

        @Override
        public void clear() {
            cleared = true;
            meta = null;
        }
    }

    private static final class FakePromptProvider implements PromptProvider {
        private boolean ignored;

        @Override
        public boolean shouldPrompt(@Nullable String remoteVersion) {
            return true;
        }

        @Override
        public void markPrompted(@NonNull String remoteVersion) {
        }

        @Override
        public boolean isIgnored(@NonNull String remoteVersion) {
            return ignored;
        }

        @Override
        public void ignore(@NonNull String remoteVersion) {
        }
    }

    private static final class FakeNetworkPolicy implements NetworkPolicy {
        private boolean wifi;

        private FakeNetworkPolicy(boolean wifi) {
            this.wifi = wifi;
        }

        @Override
        public boolean isWifiConnected() {
            return wifi;
        }
    }

    private static final class FakeResumer implements PendingDownloadResumer {
        @Override
        public void resume(@NonNull File rootDir, @NonNull String apkFilePrefix) {
        }
    }
}
