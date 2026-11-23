package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 迟到帧隔离：命令发送/超时后，按响应族登记一个有界保护窗口，窗口内到达的同族帧一律丢弃。
 *
 * <p>族由命令自身显式声明（{@link Command#getResponseFamilies()}），本类不猜族。</p>
 */
final class LateFrameGuard {
    private static final String TAG = "LateFrameGuard";

    private final Map<String, Expiry> quarantines = new HashMap<>();

    /**
     * 登记迟到帧保护窗口。原 {@code CommandScheduler.quarantine(command, node)}。
     *
     * <p>命令非空即按族登记，不再以「写已开始」为门槛：响应帧驱动语义下没有写完成回调，
     * 一旦命令进入发送/超时/完成任一路径，同族迟到帧都应被有界隔离。</p>
     *
     * @param command 已完成/中止的命令；为空则忽略
     * @param nowMs   调度线程当前时间（毫秒）
     */
    void register(Command command, long nowMs) {
        if (command == null) {
            return;
        }
        long guardMs;
        try {
            guardMs = Math.max(0L, command.getLateResponseGuardMs());
        } catch (RuntimeException e) {
            // 保护窗口读不出来时该命令不登记迟到帧隔离：宁可让同族迟到帧继续走正常匹配，
            // 也不把调度器留在半登记状态。
            HsLog.w(TAG, "读取晚到响应保护窗口失败，本命令不隔离迟到帧: "
                    + command.getClass().getSimpleName(), e);
            guardMs = 0L;
        }
        if (guardMs <= 0L) {
            return;
        }
        long expiresAt = nowMs + guardMs;
        for (String family : responseFamilies(command)) {
            Expiry current = quarantines.get(family);
            if (current == null || current.expiresAtMs < expiresAt) {
                quarantines.put(family, new Expiry(family, command, expiresAt));
            }
        }
    }

    /**
     * 该命令重新下发前需要让出的延迟（毫秒）。原 {@code CommandScheduler.quarantineDelayMs(command)}。
     */
    long delayFor(Command command, long nowMs) {
        prune(nowMs);
        long delayMs = 0L;
        for (String family : responseFamilies(command)) {
            Expiry quarantine = quarantines.get(family);
            if (quarantine != null) {
                delayMs = Math.max(delayMs, quarantine.expiresAtMs - nowMs);
            }
        }
        return Math.max(0L, delayMs);
    }

    /**
     * 该帧是否落在已登记的迟到帧保护窗口内（族=帧前 6 位大写）。原
     * {@code CommandScheduler.isQuarantinedFrame(frame)}。
     */
    boolean isQuarantined(String frame, long nowMs) {
        prune(nowMs);
        if (frame == null || frame.length() < 6) {
            return false;
        }
        String family = frame.substring(0, 6).toUpperCase(java.util.Locale.US);
        return quarantines.containsKey(family);
    }

    void clear() {
        quarantines.clear();
    }

    private void prune(long nowMs) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Expiry> entry : quarantines.entrySet()) {
            if (entry.getValue().expiresAtMs <= nowMs) {
                expired.add(entry.getKey());
            }
        }
        for (String family : expired) {
            quarantines.remove(family);
        }
    }

    private String responseFamily(Command command) {
        try {
            String family = command.getResponseFamily();
            if (family != null && !family.isEmpty()) {
                return family;
            }
        } catch (RuntimeException e) {
            // 该命令放弃自报的响应族，改以命令类名作为隔离族键。
            HsLog.w(TAG, "读取响应族失败，改用命令类名作为隔离族: "
                    + command.getClass().getName(), e);
        }
        return command.getClass().getName();
    }

    private Set<String> responseFamilies(Command command) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            Set<String> families = command.getResponseFamilies();
            if (families != null) {
                for (String family : families) {
                    if (family != null && !family.isEmpty()) {
                        result.add(family.toUpperCase(java.util.Locale.US));
                    }
                }
            }
        } catch (RuntimeException e) {
            // 该命令的迟到帧隔离退化为单族判定，由下方 responseFamily 补齐族键。
            HsLog.w(TAG, "读取响应族集合失败，隔离退化为单族: "
                    + command.getClass().getName(), e);
        }
        if (result.isEmpty()) {
            result.add(responseFamily(command).toUpperCase(java.util.Locale.US));
        }
        return result;
    }

    /** 单个响应族的保护窗口。原 {@code CommandScheduler.ResponseQuarantine}。 */
    private static final class Expiry {
        private final String family;
        private final Command command;
        private final long expiresAtMs;

        private Expiry(String family, Command command, long expiresAtMs) {
            this.family = family;
            this.command = command;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
