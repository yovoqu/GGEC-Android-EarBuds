package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoFileRef;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 文件报文的解码，兼容 OpenAI / Anthropic / Qwen 的字段拼写。 */
public final class FilesCodec {

    private FilesCodec() {
    }

    @NonNull
    public static YoFileRef decodeFile(@NonNull JSONObject json, @NonNull String rawJson) {
        String id = RetrievalJson.string(json, "id", "file_id");
        if (id == null) {
            throw new YoAiProtocolException("File object has no id", rawJson);
        }
        return YoFileRef.builder(id)
                .filename(RetrievalJson.string(json, "filename", "name"))
                .bytes(longOf(json))
                .purpose(RetrievalJson.string(json, "purpose"))
                .status(RetrievalJson.string(json, "status"))
                .createdAtMillis(RetrievalJson.millis(json, "created_at", "createdAt"))
                .rawJson(rawJson)
                .build();
    }

    private static long longOf(@NonNull JSONObject json) {
        Integer value = RetrievalJson.integer(json, "bytes", "size_bytes", "size");
        return value == null ? -1L : value;
    }

    @NonNull
    public static List<YoFileRef> decodeList(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "File list");
        JSONArray data = RetrievalJson.array(root, "data");
        if (data == null) {
            JSONObject output = RetrievalJson.object(root, "output");
            data = output == null ? null : RetrievalJson.array(output, "files");
        }
        if (data == null) {
            data = RetrievalJson.array(root, "files");
        }
        if (data == null) {
            throw new YoAiProtocolException("File list has no data array", body);
        }
        List<YoFileRef> files = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry != null) {
                files.add(decodeFile(entry, entry.toString()));
            }
        }
        return files;
    }

    @Nullable
    public static String temporaryUrl(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Temporary URL");
        String url = RetrievalJson.string(root, "url", "file_url", "download_url");
        if (url != null) {
            return url;
        }
        JSONObject output = RetrievalJson.object(root, "output");
        if (output != null) {
            url = RetrievalJson.string(output, "url", "file_url", "download_url");
        }
        return url;
    }
}
