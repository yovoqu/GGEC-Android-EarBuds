package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class YoRealtimeSessionTest {

    @Test
    public void bargeInTruncatesAtHeardAudioThenOnlyCancels() {
        RecordingLink link = new RecordingLink();
        YoRealtimeSession session = new YoRealtimeSession(link);
        session.open(config(), event -> { });

        byte[] hundredMs = new byte[4_800];
        link.emit(YoRealtimeEvent.audioChunk(hundredMs, "item_1", 0));
        session.cancelResponse();

        assertEquals(List.of("cancel", "truncate:item_1:0:100"), link.sent);

        session.cancelResponse();
        assertEquals(List.of("cancel", "truncate:item_1:0:100", "cancel"), link.sent);
    }

    @Test
    public void speechStartedTruncatesBeforeTheCallerSeesTheEvent() {
        RecordingLink link = new RecordingLink();
        YoRealtimeSession session = new YoRealtimeSession(link);
        List<String> seen = new ArrayList<>();
        session.open(config(), event -> seen.add(event.type().name()));

        link.emit(YoRealtimeEvent.audioChunk(new byte[4_800], "item_7", 1));
        link.emit(YoRealtimeEvent.speechStarted());

        assertEquals(List.of("ASSISTANT_AUDIO_CHUNK", "INPUT_SPEECH_STARTED"), seen);
        assertTrue(link.sent.contains("truncate:item_7:1:100"));
    }

    @Test
    public void manualPlaybackIgnoresArrivalUntilNoted() {
        RecordingLink link = new RecordingLink();
        YoRealtimePlayback playback = YoRealtimePlayback.manual();
        YoRealtimeSession session = new YoRealtimeSession(link, playback, true);
        session.open(config(), event -> { });

        link.emit(YoRealtimeEvent.audioChunk(new byte[4_800], "item_1", 0));
        session.cancelResponse();
        assertEquals(List.of("cancel"), link.sent);

        session.notePlayed("item_1", 0, 4_800);
        session.cancelResponse();
        assertEquals(List.of("cancel", "cancel", "truncate:item_1:0:100"), link.sent);
    }

    @Test
    public void toolResultAsksTheModelToSpeak() {
        RecordingLink link = new RecordingLink();
        YoRealtimeSession session = new YoRealtimeSession(link);
        session.open(config(), event -> { });

        session.submitToolResult("call_1", "{\"ok\":true}");

        assertEquals(List.of("tool:call_1", "create"), link.sent);
    }

    @Test
    public void audioWithoutItemIdCannotBeTruncated() {
        RecordingLink link = new RecordingLink();
        YoRealtimeSession session = new YoRealtimeSession(link);
        session.open(config(), event -> { });

        link.emit(YoRealtimeEvent.audioChunk(new byte[4_800]));
        session.cancelResponse();

        assertEquals(List.of("cancel"), link.sent);
    }

    private static YoRealtimeConfig config() {
        return YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model("gpt-realtime")
                .build();
    }

    private static final class RecordingLink implements YoRealtimeLink {
        private final List<String> sent = new ArrayList<>();
        private RealtimeSink listener;

        void emit(@NonNull YoRealtimeEvent event) {
            listener.onEvent(event);
        }

        @Override
        public void open(@NonNull YoRealtimeConfig config, @NonNull RealtimeSink listener) {
            this.listener = listener;
        }

        @Override
        public void appendAudio(@NonNull byte[] pcm16) {
        }

        @Override
        public void cancelResponse() {
            sent.add("cancel");
        }

        @Override
        public void commitInput() {
            sent.add("commit");
        }

        @Override
        public void clearInput() {
            sent.add("clear");
        }

        @Override
        public void createResponse() {
            sent.add("create");
        }

        @Override
        public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
            sent.add("truncate:" + itemId + ":" + contentIndex + ":" + audioEndMs);
        }

        @Override
        public void submitToolOutput(@NonNull String callId, @NonNull String output) {
            sent.add("tool:" + callId);
        }

        @Override
        public void close() {
            sent.add("close");
        }
    }
}
