package com.yovo.biz_ggec_hs.internal.record;

import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.record.AudioLog;

/**
 * HS 录音管线的日志转发：只做一行转发到 {@code HsLog}，不复制任何管线实现。
 */
final class HsRecordLog implements AudioLog {

    static final HsRecordLog INSTANCE = new HsRecordLog();

    private static final String TAG = "HsRecord";

    private HsRecordLog() {
    }

    @Override
    public void debug(String message) {
        HsLog.d(TAG, message);
    }

    @Override
    public void info(String message) {
        HsLog.i(TAG, message);
    }

    @Override
    public void warn(String message) {
        HsLog.w(TAG, message);
    }
}
