package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 启动检查后的统一决策。
 */
public final class LaunchDecision {

    public enum Action {
        NONE,
        SHOW_UPDATE_DIALOG,
        INSTALL_DIRECT,
        SELECT_LOCAL_APK,
        NEED_DOWNLOAD_DIR
    }

    @NonNull
    private final Action action;
    @Nullable
    private final RemoteUpdate update;
    @Nullable
    private final LocalApk localApk;
    @NonNull
    private final List<LocalApk> localApks;

    private LaunchDecision(@NonNull Action action,
                           @Nullable RemoteUpdate update,
                           @Nullable LocalApk localApk,
                           @NonNull List<LocalApk> localApks) {
        this.action = action;
        this.update = update;
        this.localApk = localApk;
        this.localApks = Collections.unmodifiableList(new ArrayList<>(localApks));
    }

    @NonNull
    public static LaunchDecision none() {
        return new LaunchDecision(Action.NONE, null, null, Collections.emptyList());
    }

    @NonNull
    public static LaunchDecision showUpdate(@NonNull RemoteUpdate update) {
        return new LaunchDecision(Action.SHOW_UPDATE_DIALOG, update, null, Collections.emptyList());
    }

    @NonNull
    public static LaunchDecision installDirect(@NonNull RemoteUpdate update,
                                               @NonNull LocalApk localApk) {
        return new LaunchDecision(
                Action.INSTALL_DIRECT, update, localApk, Collections.singletonList(localApk));
    }

    @NonNull
    public static LaunchDecision selectLocalApks(@NonNull List<LocalApk> apks) {
        return new LaunchDecision(Action.SELECT_LOCAL_APK, null, null, apks);
    }

    @NonNull
    public static LaunchDecision needDownloadDir() {
        return new LaunchDecision(Action.NEED_DOWNLOAD_DIR, null, null, Collections.emptyList());
    }

    @NonNull
    public Action action() {
        return action;
    }

    @Nullable
    public RemoteUpdate update() {
        return update;
    }

    @Nullable
    public LocalApk localApk() {
        return localApk;
    }

    @NonNull
    public List<LocalApk> localApks() {
        return localApks;
    }
}
