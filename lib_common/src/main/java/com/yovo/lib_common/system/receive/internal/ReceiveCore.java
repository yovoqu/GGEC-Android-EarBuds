package com.yovo.lib_common.system.receive.internal;

import com.yovo.lib_common.system.receive.model.IncomingShare;
import com.yovo.lib_common.system.receive.model.IncomingOpen;
import com.yovo.lib_common.log.YoLog;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description Android 入站内容协议解析器。
 *
 * <p>本类只把 Intent 转换成不可变数据，不复制 URI、不解压文件、不决定业务目录。</p>
 */
public final class ReceiveCore {

    private static final String TAG = "ReceiveCore";

    private ReceiveCore() {
    }

    /**
     * 解析 ACTION_SEND / ACTION_SEND_MULTIPLE。动作不匹配时返回 {@code null}。
     */
    @Nullable
    public static IncomingShare readShare(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action)
                && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return null;
        }

        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = IntentCompat.getParcelableArrayListExtra(
                    intent, Intent.EXTRA_STREAM, Uri.class);
            if (streams != null) {
                uris.addAll(streams);
            }
        } else {
            Uri stream = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_STREAM, Uri.class);
            if (stream != null) {
                uris.add(stream);
            }
        }
        appendClipUris(intent.getClipData(), uris);
        YoLog.d(TAG, "分享 Intent：action=" + action
                + "，类型=" + intent.getType()
                + "，URI 数=" + deduplicate(uris).size());

        return new IncomingShare(
                action,
                intent.getType(),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
                deduplicate(uris));
    }

    /**
     * 解析 ACTION_VIEW / ACTION_EDIT。以 Intent data URI 为标准载荷；动作或 data
     * 不匹配时返回 {@code null}。
     */
    @Nullable
    public static IncomingOpen readOpen(@Nullable Intent intent) {
        if (intent == null || intent.getData() == null) {
            return null;
        }
        IncomingOpen.Mode mode;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            mode = IncomingOpen.Mode.VIEW;
        } else if (Intent.ACTION_EDIT.equals(intent.getAction())) {
            mode = IncomingOpen.Mode.EDIT;
        } else {
            return null;
        }
        int grants = intent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        YoLog.d(TAG, "打开 Intent：模式=" + mode + "，URI=" + intent.getData());
        return new IncomingOpen(mode, intent.getData(), intent.getType(), grants);
    }

    private static void appendClipUris(@Nullable ClipData clipData,
                                       @NonNull List<Uri> uris) {
        if (clipData == null) {
            return;
        }
        for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            if (uri != null) {
                uris.add(uri);
            }
        }
    }

    @NonNull
    private static List<Uri> deduplicate(@NonNull List<Uri> uris) {
        return new ArrayList<>(new LinkedHashSet<>(uris));
    }
}
