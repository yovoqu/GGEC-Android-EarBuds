package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令执行最终结果（成功、超时、失败等）。
 */

public final class YoCbtCommandResult {
    private final String commandId;
    private final YoCbtCommandStatus status;
    private final YoCbtCommandResponse response;
    private final String message;

    public YoCbtCommandResult(boolean accepted) {
        this("", accepted ? YoCbtCommandStatus.SENT : YoCbtCommandStatus.FAILED, null, "");
    }

    public YoCbtCommandResult(@NonNull String commandId,
                              @NonNull YoCbtCommandStatus status,
                              @Nullable YoCbtCommandResponse response,
                              @NonNull String message) {
        this.commandId = commandId;
        this.status = status;
        this.response = response;
        this.message = message;
    }

    public boolean isAccepted() {
        return status == YoCbtCommandStatus.QUEUED || status == YoCbtCommandStatus.SENT;
    }

    @NonNull
    public String getCommandId() {
        return commandId;
    }

    @NonNull
    public YoCbtCommandStatus getStatus() {
        return status;
    }

    @Nullable
    public YoCbtCommandResponse getResponse() {
        return response;
    }

    @NonNull
    public String getMessage() {
        return message;
    }
}
