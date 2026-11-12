package com.yovo.lib_bluetooth.model.ble;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * BleScanRecord 复用与只读视图回归：empty() 必须是共享单例；rawBytesView() 返回底层数组（不逐次 clone），
 * 而 rawBytes() 仍保持防御性拷贝语义。
 */
public final class BleScanRecordTest {

    @Test
    public void emptyIsSharedSingleton() {
        assertSame(BleScanRecord.empty(), BleScanRecord.empty());
    }

    @Test
    public void rawBytesViewReturnsStableArrayWhileRawBytesClones() {
        BleScanRecord record = new BleScanRecord(
                -1, List.of(), List.of(), Map.of(), Map.of(), Integer.MIN_VALUE, null,
                new byte[]{1, 2, 3});
        assertSame(record.rawBytesView(), record.rawBytesView());
        assertNotSame(record.rawBytes(), record.rawBytes());
    }
}
