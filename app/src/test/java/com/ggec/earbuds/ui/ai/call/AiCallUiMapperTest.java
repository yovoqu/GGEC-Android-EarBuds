package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import org.junit.Test;

import java.util.List;

/** 通话页状态映射：文案、出口、光晕阶段与电平必须一次决定完。 */
public class AiCallUiMapperTest {

    private final AiCallUiMapper mapper = new AiCallUiMapper();

    @Test
    public void listeningExposesLevelAndInterruptibilityOnlyWhenRelevant() {
        AiCallUiState state = mapper.map(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.LISTENING, List.of(), 0.42f, null));

        assertEquals(AiCallUiState.Phase.LISTENING, state.phase());
        assertEquals(R.string.ai_call_phase_listening, state.title());
        assertTrue(state.live());
        assertFalse(state.interruptible());
        assertTrue(state.speakerOn());
    }

    @Test
    public void speakingIsInterruptibleAndEndedShowsTheOfflineOrb() {
        AiCallUiState speaking = mapper.map(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.SPEAKING, List.of(), 0.2f, null));
        AiCallUiState ended = mapper.map(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.ENDED, List.of(), 0.2f, null));

        assertTrue(speaking.interruptible());
        assertEquals(AiCallUiState.Phase.OFFLINE, ended.phase());
        assertTrue(ended.ended());
        assertFalse(ended.live());
    }

    @Test
    public void retryableFailuresOfferRetryAndTerminalFailuresOfferSettings() {
        AiCallUiState network = mapper.map(failed(AiFailureKind.NETWORK));
        AiCallUiState auth = mapper.map(failed(AiFailureKind.AUTHENTICATION));

        assertEquals(AiCallUiState.Action.RETRY, network.action());
        assertEquals(AiCallUiState.Action.SETTINGS, auth.action());
        assertEquals(Integer.valueOf(R.string.ai_failure_network), network.reason());
        assertEquals(Integer.valueOf(R.string.ai_failure_authentication), auth.reason());
    }

    @Test
    public void speakerPreferenceIsProjectedWithoutChangingTheVoiceState() {
        AiCallUiState state = mapper.map(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.LISTENING, List.of(), 0f, null))
                .withSpeakerOn(false);

        assertFalse(state.speakerOn());
        assertEquals(AiCallUiState.Phase.LISTENING, state.phase());
        assertTrue(state.live());
    }

    private static AiVoiceSnapshot failed(AiFailureKind kind) {
        return new AiVoiceSnapshot(AiVoiceSnapshot.State.FAILED, List.of(), 0f,
                new AiFailure(kind, "detail", null));
    }
}
