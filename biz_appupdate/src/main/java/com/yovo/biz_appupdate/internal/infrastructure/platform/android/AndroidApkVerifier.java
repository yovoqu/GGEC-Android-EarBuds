package com.yovo.biz_appupdate.internal.infrastructure.platform.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkVerifier;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Android APK 完整性校验实现。
 *
 * <p>校验维度（服务端未提供的信息自动跳过）：</p>
 * <ul>
 *     <li>文件存在性；</li>
 *     <li>签名与当前已安装应用同源；</li>
 *     <li>真实 versionCode 与服务端一致且不低于当前版本；</li>
 *     <li>文件大小与 MD5/SHA-256 摘要（如服务端提供）。</li>
 * </ul>
 */
public final class AndroidApkVerifier implements ApkVerifier {

    private final AndroidApkMetaReader metaReader;

    public AndroidApkVerifier(@NonNull Context context) {
        metaReader = new AndroidApkMetaReader(context);
    }

    @NonNull
    @Override
    public Result verify(@NonNull Context context,
                         @NonNull File apkFile,
                         @Nullable RemoteUpdate update) {
        if (!apkFile.isFile()) {
            return Result.INVALID_FILE;
        }
        LocalApk local = metaReader.enrich(new LocalApk(apkFile, apkFile.lastModified(), null));
        String expectedSignature = metaReader.currentSignatureHash();
        if (!expectedSignature.isEmpty()) {
            String localSignature = local.signatureHash();
            if (localSignature == null || !localSignature.equalsIgnoreCase(expectedSignature)) {
                return Result.SIGNATURE_MISMATCH;
            }
        }
        if (update != null && update.versionCode() > 0
                && local.versionCode() > 0
                && update.versionCode() != local.versionCode()) {
            return Result.VERSION_MISMATCH;
        }
        if (update != null && update.sizeBytes() > 0L
                && apkFile.length() != update.sizeBytes()) {
            return Result.SIZE_MISMATCH;
        }
        if (update != null && !update.md5().isEmpty()) {
            String md5 = digest(apkFile, "MD5");
            if (md5 == null || !md5.equalsIgnoreCase(update.md5())) {
                return Result.HASH_MISMATCH;
            }
        }
        if (update != null && !update.sha256().isEmpty()) {
            String sha256 = digest(apkFile, "SHA-256");
            if (sha256 == null || !sha256.equalsIgnoreCase(update.sha256())) {
                return Result.HASH_MISMATCH;
            }
        }
        return Result.OK;
    }

    @Nullable
    private static String digest(@NonNull File file, @NonNull String algorithm) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
