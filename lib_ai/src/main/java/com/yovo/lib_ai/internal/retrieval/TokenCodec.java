package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoTokenCountRequest;
import com.yovo.lib_ai.retrieval.YoTokenCountResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** token 计数报文的编码与解码。 */
public final class TokenCodec {

    private TokenCodec() {
    }

    @NonNull
    public static JSONObject encodeAnthropic(@NonNull YoTokenCountRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        JSONArray messages = new JSONArray();
        for (JSONObject message : request.messages()) {
            messages.put(message);
        }
        json.put("messages", messages);
        if (request.system() != null) {
            json.put("system", request.system());
        }
        if (!request.tools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (JSONObject tool : request.tools()) {
                tools.put(tool);
            }
            json.put("tools", tools);
        }
        return json;
    }

    @NonNull
    public static JSONObject encodeOpenAi(@NonNull YoTokenCountRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        if (request.inputText() != null) {
            json.put("input", request.inputText());
        } else if (!request.inputItems().isEmpty()) {
            JSONArray items = new JSONArray();
            for (JSONObject item : request.inputItems()) {
                items.put(item);
            }
            json.put("input", items);
        } else {
            JSONArray messages = new JSONArray();
            for (JSONObject message : request.messages()) {
                messages.put(message);
            }
            json.put("input", messages);
        }
        return json;
    }

    @NonNull
    public static YoTokenCountResult decode(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Token count response");
        Integer inputTokens = RetrievalJson.integer(root, "input_tokens");
        if (inputTokens == null) {
            throw new YoAiProtocolException("Token count response has no input_tokens", body);
        }
        return new YoTokenCountResult(
                inputTokens,
                RetrievalJson.string(root, "model"),
                body);
    }
}
