package com.yovo.biz_appupdate.api;

/**
 * 更新 UI 的呈现状态：只描述「此刻在做什么」，不含任何文案与视图。
 *
 * <p>由 {@code biz_appupdate} 的呈现器推给 {@code UpdateUiPort}，宿主负责渲染（加载态、进度条）。</p>
 */
public record UpdateUiState(boolean checking, boolean downloadInProgress, int downloadPercent) {

    private static final int PERCENT_MIN = 0;
    private static final int PERCENT_MAX = 100;

    public UpdateUiState {
        if (downloadPercent < PERCENT_MIN || downloadPercent > PERCENT_MAX) {
            throw new IllegalArgumentException("downloadPercent 必须在 0..100：" + downloadPercent);
        }
    }

    /** 空闲：不检查、不下载。 */
    public static UpdateUiState idle() {
        return new UpdateUiState(false, false, PERCENT_MIN);
    }

    public UpdateUiState withChecking(boolean value) {
        return new UpdateUiState(value, downloadInProgress, downloadPercent);
    }

    public UpdateUiState withDownload(boolean inProgress, int percent) {
        return new UpdateUiState(checking, inProgress, percent);
    }
}
