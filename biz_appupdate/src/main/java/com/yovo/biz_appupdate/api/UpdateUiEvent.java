package com.yovo.biz_appupdate.api;

import java.util.List;

/**
 * 更新 UI 的一次性事件：宿主收到就该「展示一次」，不做状态。
 *
 * <p>事件只带结构化载荷与必要的技术细节（{@code detail}），**不带用户文案**：
 * 文案、图标、弹窗形态都在宿主的资源层。</p>
 */
public sealed interface UpdateUiEvent {

    /**
     * 轻提示类事件。{@code detail} 是技术细节（检查失败的原始原因、下载失败原因），
     * 宿主可以拼进提示里，也可以只按 {@code kind} 展示。
     */
    record Notice(Kind kind, String detail) implements UpdateUiEvent {

        public enum Kind {
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

        public Notice {
            if (kind == null) {
                throw new IllegalArgumentException("notice kind 不能为空");
            }
            detail = detail == null ? "" : detail;
        }

        public static Notice of(Kind kind) {
            return new Notice(kind, "");
        }
    }

    /** 有新版本：宿主展示更新弹窗（版本号、说明、是否强制）。 */
    record ShowUpdatePrompt(String version, String description, boolean forceUpdate)
            implements UpdateUiEvent {

        public ShowUpdatePrompt {
            if (version == null || version.trim().isEmpty()) {
                throw new IllegalArgumentException("version 不能为空");
            }
            description = description == null ? "" : description;
        }
    }

    /** 手机里有多份本地安装包：宿主展示选择列表，用户选中后回调 {@code selectLocalApk(index)}。 */
    record ShowLocalApkPicker(List<String> apkFileNames) implements UpdateUiEvent {

        public ShowLocalApkPicker {
            if (apkFileNames == null || apkFileNames.isEmpty()) {
                throw new IllegalArgumentException("本地安装包列表不能为空");
            }
            apkFileNames = List.copyOf(apkFileNames);
        }
    }

    /** 安装前确认：宿主展示确认弹窗，用户确认后回调 {@code confirmInstall()}。 */
    record ConfirmInstall(String apkFileName) implements UpdateUiEvent {

        public ConfirmInstall {
            if (apkFileName == null || apkFileName.trim().isEmpty()) {
                throw new IllegalArgumentException("apkFileName 不能为空");
            }
        }
    }
}
