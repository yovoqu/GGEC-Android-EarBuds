package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 协议命令模型，封装命令 ID、载荷与超时等元数据。
 */

public final class YoCbtCommand {
    private final YoCbtPacket packet;

    public YoCbtCommand(@NonNull YoCbtPacket packet) {
        this.packet = packet;
    }

    @NonNull
    public YoCbtPacket getPacket() {
        return packet;
    }
}
