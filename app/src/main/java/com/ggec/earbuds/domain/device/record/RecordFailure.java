package com.ggec.earbuds.domain.device.record;

/**
 * 耳机录音的失败分类（design §5）。
 *
 * <p>只承载「哪一类失败」，不承载用户句子、英文技术串或 {@code Throwable.getMessage()}；
 * 用户文案在 {@code ui} 层按分类映射到 {@code strings.xml}（{@code earbuds_record_*}）。</p>
 *
 * <p>{@code RecordPort} 的失败通道（{@code RecordOutcome.Failed}、
 * {@code SyncProgressObserver.onError}）用的是同一组分类：端口只负责把 SDK 的结果归一到这里，
 * 不在端口里判断业务含义。</p>
 */
public enum RecordFailure {

    /** 没有活动连接：录音相关指令无法下发。 */
    NOT_CONNECTED,

    /** 当前设备 / 当前模式不支持该录音能力。 */
    UNSUPPORTED,

    /** 设备明确拒绝了这次指令。 */
    REJECTED,

    /** 设备在超时时间内没有响应。 */
    TIMEOUT,

    /** 已有同类任务在进行中（例如同步未结束又发起同步）。 */
    BUSY,

    /** 本地存储读写失败（落盘、读取已存文件）。 */
    IO,

    /** 设备返回了上述分类之外的错误。 */
    DEVICE_ERROR
}
