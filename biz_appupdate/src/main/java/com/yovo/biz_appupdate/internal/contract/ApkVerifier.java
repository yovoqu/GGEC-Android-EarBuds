package com.yovo.biz_appupdate.internal.contract;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;

/**
 * 安装前 APK 完整性校验端口：签名同源、真实版本、大小与摘要。
 */
public interface ApkVerifier {

    enum Result {
        OK,
        INVALID_FILE,
        VERSION_MISMATCH,
        SIGNATURE_MISMATCH,
        SIZE_MISMATCH,
        HASH_MISMATCH
    }

    @NonNull
    Result verify(@NonNull Context context,
                  @NonNull File apkFile,
                  @Nullable RemoteUpdate update);
}
