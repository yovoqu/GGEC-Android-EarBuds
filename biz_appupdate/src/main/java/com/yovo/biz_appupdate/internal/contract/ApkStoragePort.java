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

    /** 按当前命名规范生成文件名。*/
    @NonNull
    String fileName(@NonNull String versionName, int versionCode);

    @NonNull
    String currentSignatureHash();

    @NonNull
    String currentPackageName();
}
