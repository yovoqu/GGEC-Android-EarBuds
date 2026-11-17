package com.yovo.biz_ggec_wy.internal.scan;

/**
 * 扫描会话代次状态机：管理扫描代次与当前状态，确保陈旧回调被安全丢弃。
 */
final class WyScanSession {

    private final Object lock = new Object();

    private long generation;
    private boolean scanning;

    long begin() {
        synchronized (lock) {
            generation++;
            scanning = true;
            return generation;
        }
    }

    boolean isScanning() {
        synchronized (lock) {
            return scanning;
        }
    }

    boolean isCurrent(long candidateGeneration) {
        synchronized (lock) {
            return scanning && generation == candidateGeneration;
        }
    }

    boolean end(long candidateGeneration) {
        synchronized (lock) {
            if (!isCurrent(candidateGeneration)) {
                return false;
            }
            scanning = false;
            return true;
        }
    }

    void stop() {
        synchronized (lock) {
            scanning = false;
        }
    }
}
