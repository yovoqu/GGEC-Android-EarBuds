package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.LocalApk;

/**
 * APK 真实元信息读取端口（versionName / versionCode / 签名 / 包名）。
 */
public interface ApkMetaReader {

    /** 读取 APK 的真实元信息，读取失败时保留基础信息。*/
    @NonNull
    LocalApk enrich(@NonNull LocalApk base);

    /** 当前已安装应用自身的签名 SHA-256。*/
    @NonNull
    String currentSignatureHash();

    /** 当前已安装应用的包名。*/
    @NonNull
    String currentPackageName();
}
