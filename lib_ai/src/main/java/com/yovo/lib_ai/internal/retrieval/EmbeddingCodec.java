package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoEmbedding;
import com.yovo.lib_ai.retrieval.YoEmbeddingRequest;
import com.yovo.lib_ai.retrieval.YoEmbeddingResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** OpenAI 与 DashScope 两套向量报文的编码与解码。 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    /** OpenAI {@code POST /v1/embeddings} 请求体。单条用字符串，批量用数组。 */
    @NonNull
    public static JSONObject encodeOpenAi(@NonNull YoEmbeddingRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        List<String> inputs = request.inputs();
        if (inputs.size() == 1) {
            json.put("input", inputs.get(0));
        } else {
            JSONArray array = new JSONArray();
            for (String input : inputs) {
                array.put(input);
            }
            json.put("input", array);
        }
        if (request.dimensions() != null) {
            json.put("dimensions", request.dimensions());
        }
        if (request.encodingFormat() != null) {
            json.put("encoding_format", request.encodingFormat());
        }
        return json;
    }

    /** DashScope {@code .../text-embedding/text-embedding} 请求体：{@code input.texts} + {@code parameters.dimension}。 */
    @NonNull
    public static JSONObject encodeQwen(@NonNull YoEmbeddingRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        JSONObject input = new JSONObject();
        JSONArray texts = new JSONArray();
        for (String text : request.inputs()) {
            texts.put(text);
        }
        input.put("texts", texts);
        json.put("input", input);
        JSONObject parameters = new JSONObject();
        if (request.dimensions() != null) {
            parameters.put("dimension", request.dimensions());
        }
        parameters.put("output_type", "dense");
        json.put("parameters", parameters);
        return json;
    }

    @NonNull
    public static YoEmbeddingResult decodeOpenAi(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Embedding response");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            throw new YoAiProtocolException("Embedding response has no data array", body);
        }
        List<YoEmbedding> embeddings = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            embeddings.add(new YoEmbedding(
                    index(entry, "index", i),
                    vector(entry.opt("embedding"), body)));
        }
        embeddings.sort(Comparator.comparingInt(YoEmbedding::index));
        JSONObject usage = root.optJSONObject("usage");
        return new YoEmbeddingResult(
                embeddings,
                RetrievalJson.string(root, "model"),
                RetrievalJson.integer(usage, "total_tokens"),
                RetrievalJson.integer(usage, "prompt_tokens"),
                body);
    }

    @NonNull
    public static YoEmbeddingResult decodeQwen(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Embedding response");
        JSONObject output = root.optJSONObject("output");
        JSONArray data = output == null ? null : output.optJSONArray("embeddings");
        if (data == null) {
            throw new YoAiProtocolException("Embedding response has no output.embeddings array", body);
        }
        List<YoEmbedding> embeddings = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            embeddings.add(new YoEmbedding(
                    index(entry, "text_index", i),
                    vector(entry.opt("embedding"), body)));
        }
        embeddings.sort(Comparator.comparingInt(YoEmbedding::index));
        JSONObject usage = root.optJSONObject("usage");
        return new YoEmbeddingResult(
                embeddings,
                RetrievalJson.string(root, "model"),
                RetrievalJson.integer(usage, "total_tokens"),
                RetrievalJson.integer(usage, "prompt_tokens"),
                body);
    }

    private static int index(@NonNull JSONObject entry, @NonNull String key, int fallback) {
        Integer value = RetrievalJson.integer(entry, key);
        return value == null ? fallback : value;
    }

    @NonNull
    private static List<Float> vector(@Nullable Object raw, @NonNull String body) {
        if (!(raw instanceof JSONArray array)) {
            throw new YoAiProtocolException("Embedding entry has no vector array", body);
        }
        List<Float> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof Number number) {
                values.add(number.floatValue());
            } else {
                throw new YoAiProtocolException("Embedding vector contains a non-number", body);
            }
        }
        return values;
    }
}
