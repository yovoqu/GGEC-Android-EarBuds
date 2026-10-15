package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 连接键世代计数器，相同键重建连接时递增，防止陈旧引用误操作新连接。
 */
public final class YoCbtSppConnectionGeneration {
    private final int generation;
    private final long createdAtMillis;

    public YoCbtSppConnectionGeneration(int generation, long createdAtMillis) {
        this.generation = generation;
        this.createdAtMillis = createdAtMillis;
    }

    public int getGeneration() { return generation; }
    public long getCreatedAtMillis() { return createdAtMillis; }

    @NonNull
    public static YoCbtSppConnectionGeneration first() {
        return new YoCbtSppConnectionGeneration(1, System.currentTimeMillis());
    }

    @NonNull
    public YoCbtSppConnectionGeneration next() {
        return new YoCbtSppConnectionGeneration(generation + 1, System.currentTimeMillis());
    }

    public boolean isNewerThan(@NonNull YoCbtSppConnectionGeneration other) {
        return this.generation > other.generation;
    }
}
