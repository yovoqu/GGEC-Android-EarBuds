package com.yovo.lib_common.log.internal.policy;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.RollPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 滚动决策与备份链计算（纯逻辑，可单测）。
 */
public final class RollController {

    private final RollPolicy policy;

    private final FileNamePolicy namePolicy;

    private final String processSuffix;

    public RollController(@NonNull RollPolicy policy,
                          @NonNull FileNamePolicy namePolicy,
                          @NonNull String processSuffix) {
        this.policy = policy;
        this.namePolicy = namePolicy;
        this.processSuffix = processSuffix;
    }

    /** 活动文件大小达到上限且允许备份时滚动。 */
    public boolean shouldRoll(long currentSize) {
        return policy.maxBackupIndex() > 0 && currentSize >= policy.maxFileBytes();
    }

    /** 跨日（Daily 策略）时切换活动文件。 */
    public boolean shouldSwitchDay(@NonNull String currentBaseName, long now) {
        return namePolicy.type() == FileNamePolicy.Type.DAILY
                && !namePolicy.nameFor(now, processSuffix).equals(currentBaseName);
    }

    /** 活动文件对应的备份链：.1 .. .maxBackupIndex。 */
    @NonNull
    public List<File> backupChain(@NonNull File activeFile) {
        List<File> chain = new ArrayList<>(policy.maxBackupIndex());
        for (int i = 1; i <= policy.maxBackupIndex(); i++) {
            chain.add(new File(activeFile.getParentFile(),
                    activeFile.getName() + "." + i));
        }
        return chain;
    }

    @NonNull
    public RollPolicy policy() {
        return policy;
    }
}
