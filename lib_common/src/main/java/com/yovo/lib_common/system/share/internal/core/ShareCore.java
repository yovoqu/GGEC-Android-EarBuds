package com.yovo.lib_common.system.share.internal.core;

import com.yovo.lib_common.system.share.model.ArchiveTimestampStyle;
import com.yovo.lib_common.system.share.model.ShareResult;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import com.yovo.lib_common.archive.YoArchive;
import com.yovo.lib_common.archive.model.ArchivePackResult;
import com.yovo.lib_common.internal.util.FileNames;
import com.yovo.lib_common.log.YoLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description 完整的出站文件分享能力：文件校验、可选多文件归档、FileProvider URI、
 * ACTION_SEND/ACTION_SEND_MULTIPLE 与系统 Chooser。
 *
 * <p>本类只负责发送，不解析入站 Intent，也不负责把外部 URI 导入业务目录。</p>
 */
public final class ShareCore {

    private static final String TAG = "ShareCore";

    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
    private static final String ARCHIVE_DIR = "system_share";
    private static final long ARCHIVE_MAX_AGE_MS = 60L * 60L * 1000L;
    private static final String MIME_FALLBACK = "*/*";
    private static final String MIME_ZIP = "application/zip";

    private final Context context;
    private final List<File> files = new ArrayList<>();
    private String chooserTitle;
    private String archiveName = "share";
    private ArchiveTimestampStyle timestampStyle = ArchiveTimestampStyle.DATE_SECOND;
    private boolean archiveNonMediaMultiple = true;
    @Nullable
    private String fileProviderAuthority;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ShareCore(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public static ShareCore from(@NonNull Context context) {
        return new ShareCore(context);
    }

    @NonNull
    public ShareCore file(@Nullable File file) {
        if (file != null) {
            files.add(file);
        }
        return this;
    }

    @NonNull
    public ShareCore files(@Nullable List<File> source) {
        if (source != null) {
            files.addAll(source);
        }
        return this;
    }

    @NonNull
    public ShareCore chooserTitle(@Nullable String title) {
        chooserTitle = title;
        return this;
    }

    @NonNull
    public ShareCore archiveName(@Nullable String name) {
        if (name != null && !name.trim().isEmpty()) {
            archiveName = FileNames.sanitize(name, "share");
        }
        return this;
    }

    @NonNull
    public ShareCore archiveTimestamp(@Nullable ArchiveTimestampStyle style) {
        if (style != null) {
            timestampStyle = style;
        }
        return this;
    }

    /**
     * 指定 FileProvider authority；默认使用 {@code packageName + ".fileprovider"}。
     */
    @NonNull
    public ShareCore authority(@Nullable String authority) {
        this.fileProviderAuthority = authority;
        return this;
    }

    /**
     * 多个文件无法归并为纯图片或纯视频时，是否先归档为 ZIP 再分享。默认开启。
     */
    @NonNull
    public ShareCore archiveNonMediaMultiple(boolean enabled) {
        archiveNonMediaMultiple = enabled;
        return this;
    }

    @NonNull
    public ShareResult share() {
        Prepared prepared = prepare();
        if (prepared.result != ShareResult.LAUNCHED) {
            return prepared.result;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return launch(prepared.chooser);
        }
        ShareResult[] box = new ShareResult[1];
        Object lock = new Object();
        mainHandler.post(() -> {
            synchronized (lock) {
                box[0] = launch(prepared.chooser);
                lock.notifyAll();
            }
        });
        synchronized (lock) {
            while (box[0] == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ShareResult.NO_APP;
                }
            }
        }
        return box[0];
    }

    public void shareAsync(@NonNull java.util.concurrent.Executor executor,
                           @NonNull com.yovo.lib_common.system.share.YoShare.ShareCallback callback) {
        executor.execute(() -> {
            Prepared prepared = prepare();
            if (prepared.result != ShareResult.LAUNCHED) {
                mainHandler.post(() -> callback.onResult(prepared.result));
                return;
            }
            mainHandler.post(() -> callback.onResult(launch(prepared.chooser)));
        });
    }

    @NonNull
    private Prepared prepare() {
        List<File> valid = validFiles(files);
        if (valid.isEmpty()) {
            YoLog.w(TAG, "分享已忽略：没有有效文件");
            return Prepared.failed(ShareResult.NO_FILES);
        }
        YoLog.d(TAG, "开始分享：文件数=" + valid.size());

        List<File> payload = valid;
        String mimeType = commonMediaType(valid);
        if (archiveNonMediaMultiple && valid.size() > 1 && mimeType == null) {
            File archive = createArchive(valid);
            if (archive == null) {
                YoLog.w(TAG, "分享失败：创建归档出错");
                return Prepared.failed(ShareResult.ARCHIVE_ERROR);
            }
            payload = new ArrayList<>();
            payload.add(archive);
            mimeType = MIME_ZIP;
        } else if (valid.size() == 1) {
            mimeType = mimeOf(valid.get(0));
        } else if (mimeType == null) {
            mimeType = MIME_FALLBACK;
        }

        List<Uri> uris = publish(payload);
        if (uris.size() != payload.size()) {
            YoLog.w(TAG, "分享失败：已发布 " + uris.size()
                    + "/" + payload.size() + " 个 URI");
            return Prepared.failed(ShareResult.PROVIDER_ERROR);
        }
        Intent shareIntent = buildShareIntent(uris, mimeType);
        Intent chooser = Intent.createChooser(shareIntent, chooserTitle);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Prepared.ready(chooser);
    }

    @NonNull
    private ShareResult launch(@NonNull Intent chooser) {
        try {
            context.startActivity(chooser);
            YoLog.i(TAG, "分享选择器已启动");
            return ShareResult.LAUNCHED;
        } catch (ActivityNotFoundException e) {
            YoLog.w(TAG, "分享失败：没有可处理的应用", e);
            return ShareResult.NO_APP;
        }
    }

    private static final class Prepared {
        @NonNull
        final ShareResult result;
        @Nullable
        final Intent chooser;

        private Prepared(@NonNull ShareResult result, @Nullable Intent chooser) {
            this.result = result;
            this.chooser = chooser;
        }

        @NonNull
        static Prepared failed(@NonNull ShareResult result) {
            return new Prepared(result, null);
        }

        @NonNull
        static Prepared ready(@NonNull Intent chooser) {
            return new Prepared(ShareResult.LAUNCHED, chooser);
        }
    }

    /**
     * 清空多文件分享产生的缓存归档。建议在 Application 启动时调用。
     */
    public static void clearArchives(@NonNull Context context) {
        File[] files = archiveDir(context.getApplicationContext()).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        YoLog.d(TAG, "分享缓存归档已清理: " + files.length);
    }

    @NonNull
    private Intent buildShareIntent(@NonNull List<Uri> uris, @NonNull String mimeType) {
        ShareCompat.IntentBuilder builder = new ShareCompat.IntentBuilder(context)
                .setType(mimeType);
        if (uris.size() == 1) {
            builder.setStream(uris.get(0));
        } else {
            for (Uri uri : uris) {
                builder.addStream(uri);
            }
        }
        Intent intent = builder.getIntent();
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // 明确附带全部 URI，确保跨组件临时读权限覆盖所有流。
        ClipData clip = ClipData.newUri(context.getContentResolver(), "shared", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        intent.setClipData(clip);
        return intent;
    }

    @NonNull
    private List<Uri> publish(@NonNull List<File> payload) {
        List<Uri> uris = new ArrayList<>();
        String authority = fileProviderAuthority != null
                ? fileProviderAuthority
                : context.getPackageName() + FILE_PROVIDER_SUFFIX;
        for (File file : payload) {
            try {
                uris.add(FileProvider.getUriForFile(context, authority, file));
            } catch (IllegalArgumentException e) {
                YoLog.w(TAG, "无法发布文件: " + file.getAbsolutePath(), e);
            }
        }
        return uris;
    }

    @Nullable
    private File createArchive(@NonNull List<File> sources) {
        File dir = archiveDir(context);
        pruneStale(dir);
        long now = System.currentTimeMillis();
        String stamp = timestampStyle.format(now);
        File archive = new File(dir, archiveName + "_" + stamp + ".zip");
        if (archive.exists()) {
            archive = new File(dir, archiveName + "_" + stamp + "_" + now + ".zip");
        }
        ArchivePackResult result = YoArchive.pack()
                .sources(sources)
                .destination(archive)
                .execute();
        if (!result.isSuccess()) {
            YoLog.w(TAG, "归档分享失败: " + result.message());
        }
        return result.isSuccess() ? archive : null;
    }

    private static void pruneStale(@NonNull File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        long expireBefore = System.currentTimeMillis() - ARCHIVE_MAX_AGE_MS;
        for (File child : children) {
            if (child.isFile() && child.lastModified() < expireBefore) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }

    @NonNull
    private static File archiveDir(@NonNull Context context) {
        File cacheRoot = context.getExternalCacheDir();
        if (cacheRoot == null) {
            cacheRoot = context.getCacheDir();
        }
        return new File(cacheRoot, ARCHIVE_DIR);
    }

    @NonNull
    private static List<File> validFiles(@NonNull List<File> source) {
        List<File> valid = new ArrayList<>();
        for (File file : source) {
            if (file != null && file.isFile() && file.length() > 0L) {
                valid.add(file);
            }
        }
        return valid;
    }

    @NonNull
    private static String mimeOf(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.US);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return MIME_FALLBACK;
    }

    @Nullable
    private static String commonMediaType(@NonNull List<File> files) {
        boolean allImages = true;
        boolean allVideos = true;
        for (File file : files) {
            String mime = mimeOf(file);
            allImages &= mime.startsWith("image/");
            allVideos &= mime.startsWith("video/");
        }
        if (allImages) {
            return "image/*";
        }
        if (allVideos) {
            return "video/*";
        }
        return null;
    }
}
