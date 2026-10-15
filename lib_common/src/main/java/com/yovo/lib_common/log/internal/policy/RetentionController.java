package com.yovo.lib_common.log.internal.policy;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.model.RetentionPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 保留策略：天数 → 文件数 → 总字节；只处理已按命名模式过滤的日志文件。
 */
public final class RetentionController {

    private RetentionController() {
    }

    @NonNull
    public static List<File> selectForDeletion(@NonNull List<File> matchingFiles,
                                               @NonNull RetentionPolicy policy,
                                               long now) {
        List<File> candidates = new ArrayList<>(matchingFiles);
        candidates.sort(Comparator.comparingLong(File::lastModified));

        List<File> toDelete = new ArrayList<>();
        if (policy.maxDays() > 0) {
            long cutoff = now - policy.maxDays() * 86_400_000L;
            List<File> remaining = new ArrayList<>();
            for (File file : candidates) {
                if (file.lastModified() < cutoff) {
                    toDelete.add(file);
                } else {
                    remaining.add(file);
                }
            }
            candidates = remaining;
        }

        if (policy.maxFiles() > 0 && candidates.size() > policy.maxFiles()) {
            int excess = candidates.size() - policy.maxFiles();
            for (int i = 0; i < excess; i++) {
                toDelete.add(candidates.get(i));
            }
            candidates = new ArrayList<>(
                    candidates.subList(excess, candidates.size()));
        }

        if (policy.maxTotalBytes() > 0) {
            long total = 0L;
            for (File file : candidates) {
                total += file.length();
            }
            List<File> remaining = new ArrayList<>(candidates);
            while (total > policy.maxTotalBytes() && remaining.size() > 1) {
                File oldest = remaining.remove(0);
                total -= oldest.length();
                toDelete.add(oldest);
            }
        }
        return toDelete;
    }
}
