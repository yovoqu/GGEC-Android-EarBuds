package com.ggec.earbuds.ui.device.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.record.RecordState;

/**
 * 领域状态 → 渲染状态：纯函数、无分支外的默认值填充、不碰 View。
 *
 * <p>投影只搬运字段，不做业务判定；「能不能录」「是否同步中」这类判定由 {@link RecordUiState}
 * 的派生方法按状态回答，界面不再自己组合标志位。</p>
 */
public final class RecordUiMapper {

    @NonNull
    public RecordUiState map(@NonNull RecordState state) {
        return new RecordUiState(
                state.mode(),
                state.supported(),
                state.connected(),
                state.stream().phase(),
                state.stream().durationMs(),
                state.stream().amplitude(),
                state.deviceRecord().phase(),
                state.deviceRecord().durationMs(),
                state.sync().phase(),
                state.sync().fileId(),
                state.sync().percent(),
                state.sync().bytesReceived(),
                state.sync().totalBytes(),
                state.localFiles(),
                state.deviceFiles(),
                state.playingFile());
    }
}
