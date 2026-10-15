package com.yovo.biz_appupdate.internal.infrastructure.platform.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkMetaReader;
import com.yovo.biz_appupdate.internal.model.LocalApk;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 通过 PackageManager 读取 APK 真实版本、包名与签名。
 */
public final class AndroidApkMetaReader implements ApkMetaReader {

    private final Context appContext;

    public AndroidApkMetaReader(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public LocalApk enrich(@NonNull LocalApk base) {
        PackageInfo info = readArchiveInfo(base.file());
        if (info == null) {
            return base;
        }
        String signatureHash = signatureHash(info);
        return new LocalApk(
                base.file(),
                base.lastModified(),
                info.versionName != null ? info.versionName : base.versionName(),
                (int) PackageInfoCompatLong.versionCode(info),
                signatureHash,
                info.packageName);
    }

    @NonNull
    @Override
    public String currentSignatureHash() {
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(
                    appContext.getPackageName(), signatureFlags());
            String hash = signatureHash(info);
            return hash != null ? hash : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @NonNull
    @Override
    public String currentPackageName() {
        return appContext.getPackageName();
    }

    @Nullable
    private PackageInfo readArchiveInfo(@NonNull File apkFile) {
        if (!apkFile.isFile()) {
            return null;
        }
        return appContext.getPackageManager().getPackageArchiveInfo(
                apkFile.getAbsolutePath(), signatureFlags());
    }

    private static int signatureFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    @Nullable
    private static String signatureHash(@Nullable PackageInfo info) {
        if (info == null) {
            return null;
        }
        Signature[] signatures = signatures(info);
        if (signatures == null || signatures.length == 0) {
            return null;
        }
        return sha256Hex(signatures[0].toByteArray());
    }

    @Nullable
    private static Signature[] signatures(@NonNull PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) {
                return null;
            }
            Signature[] signatures = signingInfo.getApkContentsSigners();
            return signatures != null && signatures.length > 0
                    ? signatures
                    : signingInfo.getSigningCertificateHistory();
        }
        return info.signatures;
    }

    @Nullable
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static final class PackageInfoCompatLong {
        static long versionCode(@NonNull PackageInfo info) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        }
    }
}
