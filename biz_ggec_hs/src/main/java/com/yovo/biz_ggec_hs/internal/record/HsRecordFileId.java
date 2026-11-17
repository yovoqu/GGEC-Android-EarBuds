package com.yovo.biz_ggec_hs.internal.record;

import androidx.annotation.Nullable;

/**
 * 设备录音文件 ID 的契约解析。
 *
 * <p>固件侧的 fileId 是十进制下标。非十进制形态属于契约违约：调用方必须显式失败，
 * <b>绝不允许</b>回落到下标 0 去删/传第一个文件。这里不用异常做流程（解析失败不是异常路径的
 * 副作用，而是可判定的输入类型错误）。</p>
 */
final class HsRecordFileId {

    /** 下标上限位数：9 位十进制足以覆盖设备目录，同时挡住溢出。 */
    private static final int MAX_DIGITS = 9;

    private HsRecordFileId() {
    }

    /**
     * 解析设备文件下标。
     *
     * @return 非负十进制下标；不是纯十进制（空、含符号、含空格、超长）时返回 {@code null}
     */
    @Nullable
    static Integer indexOf(@Nullable String fileId) {
        if (fileId == null || fileId.isEmpty() || fileId.length() > MAX_DIGITS) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < fileId.length(); i++) {
            char digit = fileId.charAt(i);
            if (digit < '0' || digit > '9') {
                return null;
            }
            value = value * 10 + (digit - '0');
        }
        return value;
    }
}
