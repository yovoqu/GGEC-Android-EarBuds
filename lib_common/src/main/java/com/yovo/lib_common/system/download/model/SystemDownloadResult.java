package com.yovo.lib_common.system.download.model;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description 系统下载能力的发起结果。仅描述浏览器跳转 / 系统 DownloadManager 等
 * Android 系统能力是否成功发起，不代表文件最终下载完成。
 */
public final class SystemDownloadResult {

    public enum Status {
        /** 系统能力已成功发起。 */
        SUCCESS,

        /** 下载链接为空或不是合法网络地址。 */
        INVALID_URL,

        /** 设备上没有可处理该系统能力的应用 / 服务。 */
        NO_HANDLER,

        /** 其它异常导致发起失败。 */
        ERROR
    }

    private final Status status;
    private final long systemDownloadId;

    private SystemDownloadResult(Status status, long systemDownloadId) {
        this.status = status;
        this.systemDownloadId = systemDownloadId;
    }

    public static SystemDownloadResult success() {
        return new SystemDownloadResult(Status.SUCCESS, -1L);
    }

    public static SystemDownloadResult success(long systemDownloadId) {
        return new SystemDownloadResult(Status.SUCCESS, systemDownloadId);
    }

    public static SystemDownloadResult invalidUrl() {
        return new SystemDownloadResult(Status.INVALID_URL, -1L);
    }

    public static SystemDownloadResult noHandler() {
        return new SystemDownloadResult(Status.NO_HANDLER, -1L);
    }

    public static SystemDownloadResult error() {
        return new SystemDownloadResult(Status.ERROR, -1L);
    }

    public Status status() {
        return status;
    }

    public long systemDownloadId() {
        return systemDownloadId;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
