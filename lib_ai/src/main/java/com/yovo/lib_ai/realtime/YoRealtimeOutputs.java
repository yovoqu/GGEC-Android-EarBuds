package com.yovo.lib_ai.realtime;

import androidx.annotation.Nullable;

/**
 * 内部事件到公共输出的唯一翻译。{@code session.updated} 不出去。
 */
final class YoRealtimeOutputs {

    private YoRealtimeOutputs() {
    }

    @Nullable
    static YoRealtimeOutput from(YoRealtimeEvent event) {
        return switch (event.type()) {
            case OPENED -> new YoRealtimeOutput.Opened();
            case INPUT_SPEECH_STARTED -> new YoRealtimeOutput.SpeechStarted();
            case INPUT_SPEECH_STOPPED -> new YoRealtimeOutput.SpeechStopped();
            case ASSISTANT_TEXT_DELTA -> event.text() == null
                    ? null : new YoRealtimeOutput.AssistantText(event.text());
            case ASSISTANT_AUDIO_CHUNK -> event.audioPcm16() == null
                    ? null : new YoRealtimeOutput.AssistantAudio(event.audioPcm16());
            case USER_TRANSCRIPT -> event.text() == null
                    ? null : new YoRealtimeOutput.UserTranscript(event.text(), event.itemId());
            case TOOL_CALL -> event.callId() == null || event.toolName() == null || event.arguments() == null
                    ? null
                    : new YoRealtimeOutput.ToolCall(event.callId(), event.toolName(), event.arguments());
            case RESPONSE_DONE -> new YoRealtimeOutput.ResponseDone(event.responseStatus(), event.usage());
            case ERROR -> event.errorKind() == null || event.message() == null
                    ? null : new YoRealtimeOutput.Failed(event.errorKind(), event.message());
            case CLOSED -> new YoRealtimeOutput.Closed();
            case SESSION_UPDATED -> null;
        };
    }
}
