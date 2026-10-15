package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 日志活动文件名策略：固定名或按日。
 */
public final class FileNamePolicy {

    public enum Type {
        FIXED,
        DAILY
    }

    @NonNull
    private final Type type;

    @NonNull
    private final String fixedName;

    @NonNull
    private final String datePattern;

    private FileNamePolicy(@NonNull Type type,
                           @NonNull String fixedName,
                           @NonNull String datePattern) {
        this.type = type;
        this.fixedName = fixedName;
        this.datePattern = datePattern;
    }

    @NonNull
    public static FileNamePolicy fixed(@NonNull String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name must not be empty");
        }
        return new FileNamePolicy(Type.FIXED, fileName.trim(), "yyyy-MM-dd");
    }

    @NonNull
    public static FileNamePolicy daily(@NonNull String datePattern) {
        if (datePattern == null || datePattern.trim().isEmpty()) {
            throw new IllegalArgumentException("date pattern must not be empty");
        }
        return new FileNamePolicy(Type.DAILY, "app.log", datePattern.trim());
    }

    @NonNull
    public Type type() {
        return type;
    }

    /**
     * 活动文件名（含可选进程后缀）。
     */
    @NonNull
    public String nameFor(long timestampMillis, @NonNull String processSuffix) {
        if (type == Type.FIXED) {
            return withSuffix(fixedName, processSuffix);
        }
        String date = new SimpleDateFormat(datePattern, Locale.US)
                .format(new Date(timestampMillis));
        return withSuffix(date + ".log", processSuffix);
    }

    /**
     * 该策略是否管理指定文件名（含滚动备份 .1..N）。
     */
    public boolean matches(@NonNull String fileName, @NonNull String processSuffix) {
        if (type == Type.FIXED) {
            String active = withSuffix(fixedName, processSuffix);
            return fileName.matches(Pattern.quote(active) + "(\\.[0-9]+)?");
        }
        String suffix = Pattern.quote(processSuffix);
        String suffixPart = processSuffix.isEmpty()
                ? ""
                : "-" + suffix;
        Pattern pattern = Pattern.compile(
                "^" + datePatternToRegex(datePattern)
                        + suffixPart + "\\.log(\\.\\d+)?$");
        return pattern.matcher(fileName).matches();
    }

    /** 将 SimpleDateFormat 模式转换为对应数字正则（yyyy/MM/dd/HH/mm/ss/SSS）。 */
    @NonNull
    private static String datePatternToRegex(@NonNull String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() * 2);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == 'y' || c == 'M' || c == 'd'
                    || c == 'H' || c == 'm' || c == 's' || c == 'S') {
                int run = 1;
                while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                    run++;
                }
                switch (c) {
                    case 'y':
                        regex.append(run >= 4 ? "\\d{4}" : "\\d{2}");
                        break;
                    case 'M':
                    case 'd':
                    case 'H':
                    case 'm':
                    case 's':
                        regex.append("\\d{2}");
                        break;
                    case 'S':
                        regex.append("\\d{3}");
                        break;
                    default:
                        break;
                }
                i += run;
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return regex.toString();
    }

    @NonNull
    private static String withSuffix(@NonNull String name, @NonNull String processSuffix) {
        if (processSuffix.isEmpty()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return name + "-" + processSuffix;
        }
        return name.substring(0, dot) + "-" + processSuffix + name.substring(dot);
    }
}
