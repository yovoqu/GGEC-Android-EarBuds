package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令响应，携带匹配到的响应数据包与关联请求。
 */

public final class YoCbtCommandResponse {
    private final String commandId;
    private final YoCbtPacket packet;
    private final long receivedAtMillis;

    public YoCbtCommandResponse(@NonNull String commandId,
                                @NonNull YoCbtPacket packet,
                                long receivedAtMillis) {
        this.commandId = commandId;
        this.packet = packet;
        this.receivedAtMillis = receivedAtMillis;
    }

    @NonNull
    public String getCommandId() {
        return commandId;
    }

    @NonNull
    public YoCbtPacket getPacket() {
        return packet;
    }

    public long getReceivedAtMillis() {
        return receivedAtMillis;
    }
}
