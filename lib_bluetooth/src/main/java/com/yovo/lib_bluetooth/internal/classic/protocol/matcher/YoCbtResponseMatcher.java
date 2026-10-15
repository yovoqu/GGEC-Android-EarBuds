package com.yovo.lib_bluetooth.internal.classic.protocol.matcher;

import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandRequest;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 响应匹配器，将收到的数据包与待处理命令请求关联。
 */
@FunctionalInterface
public interface YoCbtResponseMatcher {

    /**
     * 判断 {@code packet} 是否为 {@code request} 的有效响应。
     */
    boolean matches(@NonNull YoCbtCommandRequest request, @NonNull YoCbtPacket packet);

    /**
     * 始终匹配。适用于同一时间仅有一个待处理命令的场景。
     * 多个命令并发时可能导致响应误路由。
     */
    YoCbtResponseMatcher FIRST_PENDING = (request, packet) -> true;

    /**
     * 创建比较响应载荷前 N 字节与命令 ID 字节的匹配器。
     * 假设响应在其载荷首字节处回显命令 ID。
     *
     * @param commandIdBytesLength 构成命令 ID 的前导字节数
     */
    @NonNull
    static YoCbtResponseMatcher byCommandIdPrefix(int commandIdBytesLength) {
        return (request, packet) -> {
            byte[] responsePayload = packet.getPayload();
            byte[] commandId = request.getCommandId().getBytes();
            if (responsePayload.length < commandIdBytesLength) return false;
            int compareLen = Math.min(commandIdBytesLength, commandId.length);
            byte[] responsePrefix = Arrays.copyOf(responsePayload, compareLen);
            byte[] requestPrefix = Arrays.copyOf(commandId, compareLen);
            return Arrays.equals(responsePrefix, requestPrefix);
        };
    }

    /**
     * 创建要求命令 ID 精确匹配的匹配器，命令 ID 以 UTF-8 字符串形式
     * 嵌入响应数据包载荷的指定偏移处。
     */
    @NonNull
    static YoCbtResponseMatcher byCommandIdString(int offset) {
        return (request, packet) -> {
            byte[] payload = packet.getPayload();
            String requestId = request.getCommandId();
            byte[] idBytes = requestId.getBytes();
            if (payload.length < offset + idBytes.length) return false;
            for (int i = 0; i < idBytes.length; i++) {
                if (payload[offset + i] != idBytes[i]) return false;
            }
            return true;
        };
    }
}
