package com.yovo.biz_ggec_hs.internal.scan;

/**
 * 扫描会话状态机：谁在扫、属于哪一代。
 *
 * <p>代次单调递增，每次 {@link #begin()} 开一代。上层把代次交给回调桥，代次与当前会话不一致时
 * {@link #isCurrent(long)} 为 false、{@link #end(long)} 返回 false，跨会话的陈旧帧、陈旧 lost
 * 与陈旧失败都在这里被丢弃，观察者收不到旧会话的回调。订阅句柄属于传输层适配，不在本类。</p>
 */
final class HsNearbyScanSession {

    private final Object lock = new Object();

    private long generation;
    private boolean scanning;

    /** 开启新一代会话，上一代立即失效。 */
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

    /** 回调所属代次是否仍是当前会话。 */
    boolean isCurrent(long candidateGeneration) {
        synchronized (lock) {
            return isCurrentLocked(candidateGeneration);
        }
    }

    /**
     * 回调结束本次会话。
     *
     * @return true 表示关闭的是当前代次；false 表示该回调属于已失效的旧代次
     */
    boolean end(long candidateGeneration) {
        synchronized (lock) {
            if (!isCurrentLocked(candidateGeneration)) {
                return false;
            }
            scanning = false;
            return true;
        }
    }

    /** 外部停止：关闭当前会话，不校验代次。 */
    void stop() {
        synchronized (lock) {
            scanning = false;
        }
    }

    private boolean isCurrentLocked(long candidateGeneration) {
        return scanning && generation == candidateGeneration;
    }
}
