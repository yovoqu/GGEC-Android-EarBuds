package com.ggec.earbuds.ui.ai.common;

import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiValidationError;

/**
 * 领域枚举到字符串资源的对应。ViewModel 和页面都要用，写在一处，漏掉一个枚举值编译就会报错。
 */
public final class AiMessages {

    private AiMessages() {
    }

    @StringRes
    public static int failure(AiFailureKind kind) {
        return switch (kind) {
            case NO_PLATFORM -> R.string.ai_failure_no_platform;
            case NO_MODEL -> R.string.ai_failure_no_model;
            case AUTHENTICATION -> R.string.ai_failure_authentication;
            case PERMISSION -> R.string.ai_failure_permission;
            case QUOTA -> R.string.ai_failure_quota;
            case RATE_LIMITED -> R.string.ai_failure_rate_limited;
            case NOT_FOUND -> R.string.ai_failure_not_found;
            case BAD_REQUEST -> R.string.ai_failure_bad_request;
            case SERVER -> R.string.ai_failure_server;
            case NETWORK -> R.string.ai_failure_network;
            case TIMEOUT -> R.string.ai_failure_timeout;
            case CANCELLED -> R.string.ai_failure_cancelled;
            case UNSUPPORTED_CAPABILITY -> R.string.ai_failure_unsupported_capability;
            case PROTOCOL -> R.string.ai_failure_protocol;
            case STORAGE -> R.string.ai_failure_storage;
            case UNKNOWN -> R.string.ai_failure_unknown;
        };
    }

    @StringRes
    public static int validation(AiValidationError error) {
        return switch (error) {
            case API_KEY_EMPTY -> R.string.ai_error_key_empty;
            case API_KEY_INVALID -> R.string.ai_error_key_invalid;
            case BASE_URL_REQUIRED -> R.string.ai_error_url_required;
            case BASE_URL_INVALID -> R.string.ai_error_url_invalid;
            case BASE_URL_NOT_HTTPS -> R.string.ai_error_url_not_https;
            case NAME_EMPTY -> R.string.ai_error_name_empty;
            case PROTOCOL_UNSUPPORTED -> R.string.ai_error_protocol_unsupported;
            case MODEL_ID_EMPTY -> R.string.ai_error_model_empty;
            case MODEL_ID_INVALID -> R.string.ai_error_model_invalid;
            case PLATFORM_UNKNOWN -> R.string.ai_error_platform_unknown;
            case PLATFORM_NOT_CONFIGURED -> R.string.ai_error_platform_not_configured;
            case MODEL_UNKNOWN -> R.string.ai_error_model_unknown;
            case PROMPT_TOO_LONG -> R.string.ai_error_prompt_too_long;
            case VOICE_NEEDS_REALTIME_MODEL -> R.string.ai_error_voice_realtime_model;
        };
    }

    @StringRes
    public static int protocol(AiProtocol protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS -> R.string.ai_protocol_chat;
            case RESPONSES -> R.string.ai_protocol_responses;
            case MESSAGES -> R.string.ai_protocol_messages;
        };
    }
}
