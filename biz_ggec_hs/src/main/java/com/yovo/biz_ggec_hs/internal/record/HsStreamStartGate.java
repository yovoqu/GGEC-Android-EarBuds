package com.yovo.biz_ggec_hs.internal.record;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 随录随传「开始成功」的多源结算闸门（纯逻辑、无 Android/BLE 依赖）。
 *
 * <p>HS02 固件对「随录随传开始」的权威成功信号是设备侧动作，而不是命令通道的
 * 99ECD5 文本回执：设备可能在命令通道完全回执缺失的情况下就已经开始出流。因此这里把
 * 「谁先到算谁」的成功判定收敛成一个只在内存里跑的小状态机：</p>
 *
 * <ul>
 *   <li>{@link Event#CMD_SUCCESS}：命令对象完成且为 SUCCESS（兼容有 99ECD5 ack 的既有固件）；</li>
 *   <li>{@link Event#CTRL_START}：音频通道控制上报 0x8004（设备侧开始出流）；</li>
 *   <li>{@link Event#AUDIO_CHUNK}：启动待确认窗口内收到第一段非控制音频切片（设备已在出流）。</li>
 * </ul>
 *
 * <p>{@link Event#CTRL_DELAY}（0x8012）只表示设备「延时中」，既不算成功也不算失败，闸门对它是空操作。
 * {@link Event#CMD_FAILED}（含 TIMEOUT）在成功之后属于迟到信号，必须被忽略；成功之前是否按失败收尾
 * 由调用方通过 {@link #trySettleFailure()} 决定，闸门本身不产出失败结果。</p>
 *
 * <p><b>只成功一次</b>：{@link #onEvent(Event)} 用 {@link AtomicBoolean} 做 CAS，成功后任何迟到事件
 * （含命令 TIMEOUT/FAILED）都返回 {@link Outcome#NONE}，保证上层 {@code reply.onReply(null)} 只触发一次。</p>
 */
final class HsStreamStartGate {

    /** 开始判定可接受的输入事件。 */
    enum Event {
        /** 命令对象完成且结果为 SUCCESS。 */
        CMD_SUCCESS,
        /** 命令对象完成且结果为失败/超时（仅记录，成功后必须被忽略）。 */
        CMD_FAILED,
        /** 音频通道控制上报 CMD_TYPE_START(0x8004)。 */
        CTRL_START,
        /** 音频通道控制上报 CMD_TYPE_DELAY(0x8012)：延时信息，非成败。 */
        CTRL_DELAY,
        /** 启动待确认窗口内的第一段非控制音频切片。 */
        AUDIO_CHUNK
    }

    /** 闸门输出：NONE 表示继续等待，SUCCESS 表示本次事件完成了一次成功结算。 */
    enum Outcome {
        NONE,
        SUCCESS
    }

    /** 是否已成功结算（成功只发生一次）。 */
    private final AtomicBoolean succeeded = new AtomicBoolean(false);
    /** 是否已终结（成功或由调用方显式按失败终结）；终结后迟到事件一律忽略。 */
    private final AtomicBoolean settled = new AtomicBoolean(false);

    /**
     * 投喂一个事件，返回该事件是否促成了「首次成功结算」。
     *
     * <p>只有 {@link Event#CMD_SUCCESS}、{@link Event#CTRL_START}、{@link Event#AUDIO_CHUNK}
     * 可能返回 {@link Outcome#SUCCESS}，且全局最多一次；{@link Event#CTRL_DELAY} 与
     * {@link Event#CMD_FAILED} 永远返回 {@link Outcome#NONE}（迟到失败被忽略）。</p>
     */
    Outcome onEvent(Event event) {
        if (event == null) {
            return Outcome.NONE;
        }
        switch (event) {
            case CMD_SUCCESS:
            case CTRL_START:
            case AUDIO_CHUNK:
                if (settled.compareAndSet(false, true)) {
                    succeeded.set(true);
                    return Outcome.SUCCESS;
                }
                return Outcome.NONE;
            case CTRL_DELAY:
            case CMD_FAILED:
            default:
                return Outcome.NONE;
        }
    }

    /**
     * 在「尚无成功」的前提下把本次启动按失败终结一次；若已成功或已终结则返回 {@code false}。
     *
     * <p>失败不在 {@link #onEvent(Event)} 里产出，是为了让「命令 FAILED/timeout」只在没有
     * 任何设备侧成功信号时才有资格收尾，避免 HS02 因命令通道缺回执被误判失败。</p>
     */
    boolean trySettleFailure() {
        return settled.compareAndSet(false, true);
    }

    /** 是否已经终结（成功或失败）。 */
    boolean isSettled() {
        return settled.get();
    }

    /** 是否已经成功结算（用于判断命令对象是否需要主动 replay 释放串行队列）。 */
    boolean hasSucceeded() {
        return succeeded.get();
    }
}
