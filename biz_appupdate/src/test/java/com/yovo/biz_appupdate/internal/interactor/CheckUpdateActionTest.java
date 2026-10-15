package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import org.junit.Test;

import java.io.File;

public final class CheckUpdateActionTest {

    private final FakeCheckProvider checkProvider = new FakeCheckProvider();
    private final FakeMetaStore metaStore = new FakeMetaStore();
    private final UpdatePolicy policy = new UpdatePolicy(
            "https://init.example.com/app.apk",
            false,
            true,
            "Auto_Tools",
            1000L,
            5000L);

    @Test
    public void newVersionRecordsResolvedUrl() {
        checkProvider.result = new RemoteUpdate(
                true, "1.5.3", 155, "desc", "  https://remote.example.com/app.apk  ", false);

        CaptureCallback callback = new CaptureCallback();
        action().run(callback);

        assertEquals("https://remote.example.com/app.apk", callback.success.downloadUrl());
        assertEquals("https://remote.example.com/app.apk", metaStore.meta.downloadUrl());
    }

    @Test
    public void noNewVersionClearsMeta() {
        metaStore.meta = new UpdateMeta(
                "1.5.2", 154, "old", "https://old.example.com/app.apk", false, 1L, null);
        checkProvider.result = new RemoteUpdate(
                false, "1.5.2", 154, "desc", "https://remote.example.com/app.apk", false);

        CaptureCallback callback = new CaptureCallback();
        action().run(callback);

        assertTrue(callback.success != null && !callback.success.hasNewVersion());
        assertTrue(metaStore.cleared);
        assertNull(metaStore.meta);
    }

    @Test
    public void newVersionWithoutUsableUrlErrors() {
        UpdatePolicy emptyPolicy = new UpdatePolicy(
                "not-a-url", false, true, "Auto_Tools", 1000L, 5000L);
        checkProvider.result = new RemoteUpdate(
                true, "1.5.3", 155, "desc", "/relative.apk", false);

        CaptureCallback callback = new CaptureCallback();
        new CheckUpdateAction(
                checkProvider,
                new FakeVersionProvider(),
                emptyPolicy,
                new FakeNetworkPolicy(true),
                metaStore).run(callback);

        assertEquals(CheckUpdateAction.ERROR_NO_DOWNLOAD_URL, callback.error);
        assertNull(callback.success);
        assertNull(metaStore.meta);
    }

    private CheckUpdateAction action() {
        return new CheckUpdateAction(
                checkProvider,
                new FakeVersionProvider(),
                policy,
                new FakeNetworkPolicy(true),
                metaStore);
    }

    private static final class CaptureCallback implements UpdateCheckProvider.Callback {
        RemoteUpdate success;
        String error;

        @Override
        public void onSuccess(@NonNull RemoteUpdate update) {
            success = update;
        }

        @Override
        public void onError(@NonNull String message) {
            error = message;
        }
    }

    private static final class FakeCheckProvider implements UpdateCheckProvider {
        RemoteUpdate result;

        @Override
        public void check(@NonNull String currentVersion, @NonNull Callback callback) {
            callback.onSuccess(result);
        }
    }

    private static final class FakeVersionProvider implements VersionProvider {
        @NonNull
        @Override
        public String currentVersionName() {
            return "1.5.2";
        }

        @Override
        public int currentVersionCode() {
            return 154;
        }
    }

    private static final class FakeNetworkPolicy implements NetworkPolicy {
        private final boolean wifi;

        FakeNetworkPolicy(boolean wifi) {
            this.wifi = wifi;
        }

        @Override
        public boolean isWifiConnected() {
            return wifi;
        }
    }

    private static final class FakeMetaStore implements UpdateMetaStore {
        @Nullable
        UpdateMeta meta;
        boolean cleared;

        @Override
        public void record(@NonNull RemoteUpdate update) {
            meta = new UpdateMeta(
                    update.version(), update.versionCode(), update.description(),
                    update.downloadUrl(), update.forceUpdate(),
                    1L, null);
        }

        @Override
        public void recordDownloadedApk(@NonNull RemoteUpdate update, @NonNull File apkFile) {
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
}
