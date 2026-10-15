package com.yovo.lib_common.log.internal.sink;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.LogSink;

import java.util.ArrayList;
import java.util.List;

/**
 * Logcat Sink：同步输出，单条消息按 ~4000 UTF-16 单元分片，避免静默截断。
 */
public final class LogcatSink implements LogSink {

    private static final int MAX_UNITS = 4000;

    @Nullable
    private final LogLevel minLevel;

    public LogcatSink(@Nullable LogLevel minLevel) {
        this.minLevel = minLevel;
    }

    @Override
    public void write(@NonNull LogRecord record) {
        if (minLevel != null && !record.level().isEnabled(minLevel)) {
            return;
        }
        try {
            String tag = record.tag();
            List<String> chunks = chunk(record.message());
            for (int i = 0; i < chunks.size(); i++) {
                println(record.level(), tag, chunks.get(i));
            }
            if (record.throwable() != null) {
                String trace = Log.getStackTraceString(record.throwable());
                if (trace != null && !trace.isEmpty()) {
                    List<String> traceChunks = chunk(trace);
                    for (String traceChunk : traceChunks) {
                        println(record.level(), tag, traceChunk);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Logcat 输出失败不得影响业务。
        }
    }

    @Override
    public void flush() {
        // logcat 同步输出，无需刷盘。
    }

    @Override
    public void close() {
        // no-op
    }

    @NonNull
    static List<String> chunk(@NonNull String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= MAX_UNITS) {
            chunks.add(text);
            return chunks;
        }
        int count = (text.length() + MAX_UNITS - 1) / MAX_UNITS;
        int offset = 0;
        int index = 1;
        while (offset < text.length()) {
            int end = Math.min(offset + MAX_UNITS, text.length());
            chunks.add("(" + index + "/" + count + ") "
                    + text.substring(offset, end));
            offset = end;
            index++;
        }
        return chunks;
    }

    private static void println(@NonNull LogLevel level,
                                @NonNull String tag,
                                @NonNull String message) {
        switch (level) {
            case VERBOSE:
                Log.println(Log.VERBOSE, tag, message);
                break;
            case DEBUG:
                Log.println(Log.DEBUG, tag, message);
                break;
            case INFO:
                Log.println(Log.INFO, tag, message);
                break;
            case WARN:
                Log.println(Log.WARN, tag, message);
                break;
            case ERROR:
                Log.println(Log.ERROR, tag, message);
                break;
            case ASSERT:
                Log.println(Log.ASSERT, tag, message);
                break;
        }
    }
}
