package com.yovo.biz_ggec_wy.internal.record;

import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;
import com.yovo.lib_ggec_audio.record.AudioLog;

/**
 * WY 录音管线的日志转发：只做一行转发到 {@code WyLog}，不复制任何管线实现。
 */
public final class WyRecordLog implements AudioLog {

    public static final WyRecordLog INSTANCE = new WyRecordLog();

    private static final String TAG = "WyRecord";

    private WyRecordLog() {
    }

    @Override
    public void debug(String message) {
        WyLog.d(TAG, message);
    }

    @Override
    public void info(String message) {
        WyLog.i(TAG, message);
    }

    @Override
    public void warn(String message) {
        WyLog.w(TAG, message);
    }
}
