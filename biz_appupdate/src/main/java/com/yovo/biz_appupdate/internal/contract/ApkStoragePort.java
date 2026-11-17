package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.LocalApk;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * 下载目录存储端口。
 *
 * <p>只提供“扫描、删除、命名”等存储原语，不包含保留策略；
 * 策略由 interactor 层负责。</p>
 */
public interface ApkStoragePort {

    /** 扫描当前规范下的全部自家 APK（含真实元信息）。*/
    @NonNull
    List<LocalApk> listApks();

    /** 删除指定安装包（SAF 文档 + 本地缓存）。*/
    boolean deleteApk(@NonNull LocalApk apk);

    /** 删除所有属于本 SDK 前缀管理、但不在保留集合中的文件（含旧命名/无效文件）。*/
    void deleteOwnedFilesExcept(@NonNull Collection<File> keepFiles);

    @NonNull
    File rootDir();

    /** 确保下载目录可用（SAF 授权检查 + 目录创建）；不可用时返回 false。 */
    boolean ensureReady();

    /**
     * 下载完成后的落地策略：非 SAF 目录为空操作，SAF 目录把文件复制进授权树。
     *
     * <p>SAF 只是存储策略的一种，策略留在实现里；调用方只关心「文件是否已进目标目录」。</p>
     */
    boolean finishDownload(@NonNull File downloadedFile);

    /** 按当前命名规范生成文件名。*/
    @NonNull
    String fileName(@NonNull String versionName, int versionCode);

    @NonNull
    String currentSignatureHash();

    @NonNull
    String currentPackageName();
}
