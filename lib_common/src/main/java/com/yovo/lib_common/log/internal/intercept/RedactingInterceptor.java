package com.yovo.lib_common.log.internal.intercept;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.model.LogInterceptor;
import com.yovo.lib_common.log.model.LogRecord;

import java.util.regex.Pattern;

/**
 * 消息 PII 脱敏：邮箱 / Bearer Token / 长数字串。默认不扫描 stacktrace。
 */
public final class RedactingInterceptor implements LogInterceptor {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{16,}\\b");

    private static final String REDACTED = "[REDACTED]";

    @Override
    @NonNull
    public LogRecord intercept(@NonNull LogRecord record) {
        String message = record.message();
        String redacted = EMAIL.matcher(message).replaceAll(REDACTED);
        redacted = BEARER.matcher(redacted).replaceAll("bearer " + REDACTED);
        redacted = LONG_DIGITS.matcher(redacted).replaceAll(REDACTED);
        if (redacted.equals(message)) {
            return record;
        }
        return new LogRecord(
                record.timestampMillis(),
                record.level(),
                record.tag(),
                redacted,
                record.threadName(),
                record.threadId(),
                record.throwable(),
                record.context());
    }
}
