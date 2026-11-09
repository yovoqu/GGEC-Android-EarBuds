package com.yovo.lib_bluetooth.model.ble;

/**
 * 流式合包决策结果（配合 {@link BleDataMerger} 判断 Notify 响应帧是否接收完整）。
 */
public enum BleMergeResult {
    /** 尚需更多后续数据包。 */
    NEED_MORE,
    /** 已收到完整逻辑报文，结束合并。 */
    COMPLETE,
    /** 当前包校验不通过（帧头/CRC 异常），视为失败。 */
    INVALID
}
