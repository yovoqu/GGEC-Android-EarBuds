package com.ggec.earbuds.ui.device.record;

import androidx.annotation.NonNull;

/**
 * 录音页面一次性事件。
 */
public sealed interface RecordEvent {
    record ToastMessage(@NonNull String message) implements RecordEvent {}
}
