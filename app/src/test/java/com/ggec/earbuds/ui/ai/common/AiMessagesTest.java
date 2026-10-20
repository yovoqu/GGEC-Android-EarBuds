package com.ggec.earbuds.ui.ai.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiValidationError;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AiMessagesTest {

    @Test
    public void everyFailureKindHasItsOwnMessage() {
        Set<Integer> seen = new HashSet<>();
        for (AiFailureKind kind : AiFailureKind.values()) {
            int message = AiMessages.failure(kind);
            assertNotEquals(kind.name(), 0, message);
            assertTrue(kind + " shares its message", seen.add(message));
        }
        assertEquals(R.string.ai_failure_authentication, AiMessages.failure(AiFailureKind.AUTHENTICATION));
    }

    @Test
    public void everyValidationErrorHasItsOwnMessage() {
        Set<Integer> seen = new HashSet<>();
        for (AiValidationError error : AiValidationError.values()) {
            int message = AiMessages.validation(error);
            assertNotEquals(error.name(), 0, message);
            assertTrue(error + " shares its message", seen.add(message));
        }
        assertEquals(R.string.ai_error_url_not_https, AiMessages.validation(AiValidationError.BASE_URL_NOT_HTTPS));
    }

    @Test
    public void everyProtocolHasALabel() {
        assertEquals(R.string.ai_protocol_chat, AiMessages.protocol(AiProtocol.CHAT_COMPLETIONS));
        assertEquals(R.string.ai_protocol_responses, AiMessages.protocol(AiProtocol.RESPONSES));
        assertEquals(R.string.ai_protocol_messages, AiMessages.protocol(AiProtocol.MESSAGES));
    }
}
