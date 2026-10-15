package com.yovo.lib_common.system.share.model;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 多文件分享归档的时间戳格式。
 */
public enum ArchiveTimestampStyle {
    DATE_SECOND {
        @NonNull
        @Override
        public String format(long nowMillis) {
            return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                    .format(new Date(nowMillis));
        }
    },
    EPOCH_MILLIS {
        @NonNull
        @Override
        public String format(long nowMillis) {
            return Long.toString(nowMillis);
        }
    };

    @NonNull
    public abstract String format(long nowMillis);
}
