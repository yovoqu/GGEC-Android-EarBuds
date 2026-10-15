package com.yovo.lib_ai.internal.chat;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.YoChatChoice;
import com.yovo.lib_ai.chat.YoChatChunk;
import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatToolCall;
import com.yovo.lib_ai.chat.YoChatUsage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public final class ChatAccumulator {

    @Nullable
    private String id;
    @Nullable
    private String model;
    @Nullable
    private YoChatUsage usage;
    private final TreeMap<Integer, ChoiceAcc> choices = new TreeMap<>();

    public void add(YoChatChunk chunk) {
        if (chunk.id() != null) {
            id = chunk.id();
        }
        if (chunk.model() != null) {
            model = chunk.model();
        }
        if (chunk.usage() != null) {
            usage = chunk.usage();
        }
        for (YoChatChoice choice : chunk.choices()) {
            choices.computeIfAbsent(choice.index(), ChoiceAcc::new).add(choice);
        }
    }

    public YoChatCompletion toCompletion() {
        List<YoChatChoice> decoded = new ArrayList<>();
        JSONArray choiceJson = new JSONArray();
        for (ChoiceAcc choice : choices.values()) {
            YoChatChoice built = choice.build();
            decoded.add(built);
            choiceJson.put(choice.toJson(built));
        }
        JSONObject raw = new JSONObject();
        try {
            if (id != null) {
                raw.put("id", id);
            }
            raw.put("object", "chat.completion");
            if (model != null) {
                raw.put("model", model);
            }
            raw.put("choices", choiceJson);
            if (usage != null) {
                JSONObject usageJson = new JSONObject();
                usageJson.put("prompt_tokens", usage.promptTokens());
                usageJson.put("completion_tokens", usage.completionTokens());
                usageJson.put("total_tokens", usage.totalTokens());
                raw.put("usage", usageJson);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
        return new YoChatCompletion(id, model, decoded, usage, raw.toString());
    }

    private static final class ChoiceAcc {
        private final int index;
        private String role = "assistant";
        private final StringBuilder content = new StringBuilder();
        @Nullable
        private String finishReason;
        private final TreeMap<Integer, ToolAcc> toolCalls = new TreeMap<>();

        private ChoiceAcc(int index) {
            this.index = index;
        }

        private void add(YoChatChoice choice) {
            finishReason = choice.finishReason() == null ? finishReason : choice.finishReason();
            YoChatMessage message = choice.message();
            if (message == null) {
                return;
            }
            if (message.role() != null && !message.role().isBlank() && !"assistant".equals(message.role())) {
                role = message.role();
            } else if (message.content() != null || message.toolCalls().length > 0) {
                role = message.role();
            }
            if (message.content() != null) {
                content.append(message.content());
            }
            for (YoChatToolCall call : message.toolCalls()) {
                int key = call.index() >= 0 ? call.index() : toolCalls.size();
                toolCalls.computeIfAbsent(key, ToolAcc::new).add(call);
            }
        }

        private YoChatChoice build() {
            YoChatMessage.Builder builder = YoChatMessage.builder().role(role);
            if (content.length() > 0) {
                builder.content(content.toString());
            }
            if (!toolCalls.isEmpty()) {
                List<YoChatToolCall> calls = new ArrayList<>();
                for (ToolAcc tool : toolCalls.values()) {
                    calls.add(tool.build());
                }
                builder.toolCalls(calls.toArray(YoChatToolCall[]::new));
            }
            if (content.length() == 0 && toolCalls.isEmpty()) {
                builder.content("");
            }
            return new YoChatChoice(index, builder.build(), finishReason);
        }

        private JSONObject toJson(YoChatChoice choice) {
            JSONObject json = new JSONObject();
            try {
                json.put("index", index);
                json.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
                JSONObject message = new JSONObject();
                YoChatMessage body = choice.message();
                message.put("role", body == null ? role : body.role());
                message.put("content", content.length() == 0 ? JSONObject.NULL : content.toString());
                if (!toolCalls.isEmpty()) {
                    JSONArray calls = new JSONArray();
                    List<ToolAcc> tools = new ArrayList<>(toolCalls.values());
                    tools.sort(Comparator.comparingInt(tool -> tool.index));
                    for (ToolAcc tool : tools) {
                        calls.put(tool.toJson());
                    }
                    message.put("tool_calls", calls);
                }
                json.put("message", message);
            } catch (JSONException exception) {
                throw new IllegalStateException(exception);
            }
            return json;
        }
    }

    private static final class ToolAcc {
        private final int index;
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();

        private ToolAcc(int index) {
            this.index = index;
        }

        private void add(YoChatToolCall call) {
            if (!call.id().isEmpty()) {
                id = call.id();
            }
            if (!call.name().isEmpty()) {
                name = call.name();
            }
            arguments.append(call.arguments());
        }

        private YoChatToolCall build() {
            return new YoChatToolCall(index, id, name, arguments.toString());
        }

        private JSONObject toJson() throws JSONException {
            JSONObject function = new JSONObject();
            function.put("name", name);
            function.put("arguments", arguments.toString());
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("type", "function");
            json.put("function", function);
            return json;
        }
    }
}
