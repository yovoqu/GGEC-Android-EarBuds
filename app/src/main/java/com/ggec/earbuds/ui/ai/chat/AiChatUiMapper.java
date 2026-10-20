package com.ggec.earbuds.ui.ai.chat;

import com.ggec.earbuds.domain.ai.chat.AiChatState;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageFailure;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把领域状态翻译成界面状态。这里只做判断和取舍，不接触任何视图；文字由页面按资源渲染。
 */
public final class AiChatUiMapper {

    /**
     * @param reasoningOverrides 用户手动展开或折叠过的思考块，没有操作过的按默认规则：
     *                           正文还没开始时展开，之后折叠
     */
    public AiChatUiState map(AiChatState state, Map<String, Boolean> reasoningOverrides) {
        List<AiMessage> messages = state.conversation().messages();
        List<AiMessageUi> items = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            items.add(map(messages.get(i), i == messages.size() - 1, state, reasoningOverrides));
        }
        AiActiveModel model = state.model();
        String label = model == null ? "" : model.platform().name() + " · " + model.model().name();
        return new AiChatUiState(
                state.loaded(),
                label,
                items,
                welcome(state),
                new AiChatUiState.Composer(state.ready(), state.generating(), model == null ? null : label),
                state.storageFailed());
    }

    private static AiChatUiState.Welcome welcome(AiChatState state) {
        if (!state.loaded() || !state.conversation().empty()) {
            return AiChatUiState.Welcome.NONE;
        }
        return switch (state.readiness()) {
            case NO_PLATFORM -> AiChatUiState.Welcome.NEEDS_PLATFORM;
            case NO_MODEL -> AiChatUiState.Welcome.NEEDS_MODEL;
            case READY -> AiChatUiState.Welcome.SUGGESTIONS;
        };
    }

    private static AiMessageUi map(AiMessage message, boolean last, AiChatState state,
                                   Map<String, Boolean> reasoningOverrides) {
        boolean streaming = message.status() == AiMessageStatus.STREAMING;
        boolean showReasoning = state.preferences().showReasoning() && message.reasoning() != null;
        Boolean override = reasoningOverrides.get(message.id());
        boolean expanded = override != null ? override : streaming && message.text().isEmpty();
        AiMessageUi.Notice notice = notice(message);
        boolean canRegenerate = last && !message.user() && !state.generating() && state.ready();
        AiMessageFailure failure = message.failure();
        return new AiMessageUi(
                message.id(),
                message.user(),
                message.text(),
                showReasoning ? message.reasoning() : null,
                expanded,
                streaming,
                notice,
                failure == null ? null : failure.kind(),
                failure == null ? "" : failure.detail(),
                message.modelLabel() == null ? "" : message.modelLabel(),
                message.elapsedMillis(),
                message.usage() == null ? 0 : message.usage().outputTokens(),
                canRegenerate,
                canRegenerate && notice == AiMessageUi.Notice.TRUNCATED,
                !state.generating());
    }

    private static AiMessageUi.Notice notice(AiMessage message) {
        if (message.user()) {
            return AiMessageUi.Notice.NONE;
        }
        return switch (message.status()) {
            case STREAMING -> AiMessageUi.Notice.NONE;
            case ERROR -> AiMessageUi.Notice.ERROR;
            case STOPPED -> AiMessageUi.Notice.STOPPED;
            case COMPLETE -> {
                if (message.finishReason() == AiFinishReason.LENGTH) {
                    yield AiMessageUi.Notice.TRUNCATED;
                }
                if (message.finishReason() == AiFinishReason.FILTERED) {
                    yield AiMessageUi.Notice.FILTERED;
                }
                yield message.blank() ? AiMessageUi.Notice.EMPTY : AiMessageUi.Notice.NONE;
            }
        };
    }
}
