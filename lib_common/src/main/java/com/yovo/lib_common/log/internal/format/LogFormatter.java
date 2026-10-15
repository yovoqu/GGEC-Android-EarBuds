package com.yovo.lib_common.log.internal.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.model.LogRecord;

import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 文件行格式：yyyy-MM-dd HH:mm:ss.SSS LEVEL/tag [thread]: message {ctx}。
 */
public final class LogFormatter {

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() ->
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    @NonNull
    public String format(@NonNull LogRecord record,
                         boolean includeThread,
                         boolean includeContext) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(DATE_FORMAT.get().format(new Date(record.timestampMillis())))
                .append(' ')
                .append(record.level().name())
                .append('/')
                .append(record.tag());
        if (includeThread) {
            sb.append(" [").append(record.threadName()).append(']');
        }
        sb.append(": ").append(record.message());
        if (includeContext && !record.context().isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<String, String> entry : record.context().entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            sb.append('}');
        }
        return sb.toString();
    }

    /** 将异常堆栈格式化为多行文本；null 返回空串。 */
    @NonNull
    public String throwableText(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter(256);
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
