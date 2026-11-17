package com.ggec.earbuds.domain.update.port;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * APP 更新的宿主端口：更新会话与界面之间唯一的契约。
 *
 * <p>端口只描述「宿主能收到什么」与「用户能做什么」，词汇全部留在本文件里：
 * {@link State}（此刻在做什么）、{@link Event}（一次性呈现）、{@link Effect}（需要宿主动作的
 * 系统页面）、{@link Actions}（用户动作）。实现方在 {@code provider/biz_appupdate}，
 * 渲染方在 {@code ui/update}；两边互不 import，由装配根接线。</p>
 *
 * <p>这里没有 {@code Context}/{@code Intent}/{@code Activity}：纯 Java 的领域层不认框架类型，
 * 拉起系统页面的做法留在平台层实现里。</p>
 */
public interface UpdateUiPort {

    /** 用户动作面：界面持有它，点击落到端口实现上。 */
    @NonNull
    Actions actions();

    /** 挂上渲染面：实现方随即开始启动检查，结果经 {@code renderer} 回灌。 */
    void attach(@NonNull Renderer renderer);

    /** 宿主回到前台：安装权限设置页返回后的结果在这里收口。 */
    void onHostResume();

    /** 解绑：不再回调渲染面，也不改会话状态。 */
    void detach();

    /** 渲染面：由界面实现，只做绘制与转发，不回头改流程。 */
    interface Renderer {

        /** 状态整体替换（幂等渲染）。 */
        void onState(@NonNull State state);

        /** 一次性呈现，展示一次即弃。 */
        void onEvent(@NonNull Event event);

        /** 解绑：关掉自己开过的一次性呈现（弹窗），之后不再被调用。 */
        void dismissAll();
    }

    /** 用户动作：方法名就是用户做了什么，参数只有用户确实掌握的值。 */
    interface Actions {

        /** 用户点「检查更新」。 */
        void onCheckUpdate();

        /** 用户在更新弹窗里选了下载方式。 */
        void onDownloadChosen(@NonNull DownloadChoice choice);

        /** 用户选「稍后」。 */
        void onUpdateDeferred();

        /** 用户在安装确认弹窗里点「安装」。 */
        void onInstallConfirmed();

        /** 用户在本地安装包列表里选了第 {@code index} 项。 */
        void onLocalApkChosen(int index);
    }

    /** 此刻在做什么：只描述状态，不含文案与视图。 */
    record State(boolean checking, boolean downloadInProgress, int downloadPercent) {

        private static final int PERCENT_MIN = 0;
        private static final int PERCENT_MAX = 100;

        public State {
            if (downloadPercent < PERCENT_MIN || downloadPercent > PERCENT_MAX) {
                throw new IllegalArgumentException("downloadPercent 必须在 0..100：" + downloadPercent);
            }
        }
    }

    /** 一次性呈现：轻提示、更新弹窗、本地安装包选择、安装确认。 */
    sealed interface Event {

        /** 轻提示。{@code detail} 是会话给的技术细节（失败原因、文件名），由资源文案包起来显示。 */
        record Notify(@NonNull Notice notice, @NonNull String detail) implements Event {

            public Notify {
                Objects.requireNonNull(notice, "notice");
                Objects.requireNonNull(detail, "detail");
            }
        }

        /** 有新版本：弹更新弹窗，用户选下载方式或稍后。 */
        record ShowUpdatePrompt(@NonNull Prompt prompt) implements Event {

            public ShowUpdatePrompt {
                Objects.requireNonNull(prompt, "prompt");
            }
        }

        /** 手机里有多份本地安装包：让用户选一份。 */
        record ShowLocalApkPicker(@NonNull List<String> fileNames) implements Event {

            public ShowLocalApkPicker {
                if (fileNames == null || fileNames.isEmpty()) {
                    throw new IllegalArgumentException("本地安装包列表不能为空");
                }
                fileNames = List.copyOf(fileNames);
            }
        }

        /** 安装前确认。 */
        record ConfirmInstall(@NonNull String apkFileName) implements Event {

            public ConfirmInstall {
                if (apkFileName == null || apkFileName.trim().isEmpty()) {
                    throw new IllegalArgumentException("apkFileName 不能为空");
                }
            }
        }
    }

    /** 一次「有新版本」的呈现：版本号、说明、是否强制。文案在资源层拼。 */
    record Prompt(@NonNull String version, @NonNull String description, boolean forceUpdate) {

        public Prompt {
            if (version == null || version.trim().isEmpty()) {
                throw new IllegalArgumentException("version 不能为空");
            }
            description = description == null ? "" : description;
        }
    }

    /** 下载方式；界面只负责让用户选，语义由会话决定。 */
    enum DownloadChoice {
        INTERNAL,
        SYSTEM,
        BROWSER
    }

    /** 一次性轻提示的分类；文案按 kind 选资源，{@code detail} 只作为附加说明填进去。 */
    enum Notice {
        ALREADY_LATEST,
        CHECK_FAILED,
        DOWNLOAD_STARTED_SYSTEM,
        DOWNLOAD_STARTED_BROWSER,
        DOWNLOAD_START_FAILED_INTERNAL,
        DOWNLOAD_START_FAILED_SYSTEM,
        DOWNLOAD_START_FAILED_BROWSER,
        DOWNLOAD_COMPLETED,
        DOWNLOAD_FAILED,
        INSTALL_INVALID_FILE,
        INSTALL_VERIFY_FAILED,
        INSTALL_NO_INSTALLER,
        INSTALL_PERMISSION_DENIED
    }

    /** 需要宿主动作的系统页面：会话只说「要做哪件事」，怎么做由宿主实现。 */
    enum Effect {
        REQUEST_INSTALL_PERMISSION,
        OPEN_APP_SETTINGS
    }
}
