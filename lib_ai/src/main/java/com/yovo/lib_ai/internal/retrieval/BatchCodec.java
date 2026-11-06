package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoBatchJob;
import com.yovo.lib_ai.retrieval.YoBatchRequest;
import com.yovo.lib_ai.retrieval.YoBatchResult;
import com.yovo.lib_ai.retrieval.YoBatchStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 批量任务报文的编码与解码，含状态机映射与 JSONL 结果解析。 */
public final class BatchCodec {

    private BatchCodec() {
    }

    @NonNull
    public static JSONObject encodeSubmit(@NonNull YoBatchRequest request, boolean anthropic) throws JSONException {
        JSONObject json = new JSONObject();
        if (anthropic) {
            JSONArray requests = new JSONArray();
            for (JSONObject inline : request.requests()) {
                requests.put(inline);
            }
            json.put("requests", requests);
            return json;
        }
        if (request.inputFileId() != null) {
            json.put("input_file_id", request.inputFileId());
        }
        if (request.endpoint() != null) {
            json.put("endpoint", request.endpoint());
        }
        if (request.completionWindow() != null) {
            json.put("completion_window", request.completionWindow());
        }
        if (request.metadata() != null) {
            json.put("metadata", request.metadata());
        }
        return json;
    }

    @NonNull
    public static YoBatchJob decodeJob(@NonNull String body, boolean anthropic) {
        JSONObject root = RetrievalJson.parseObject(body, "Batch");
        String id = RetrievalJson.string(root, "id");
        if (id == null) {
            throw new YoAiProtocolException("Batch has no id", body);
        }
        YoBatchJob.Builder job = YoBatchJob.builder(id)
                .rawJson(body)
                .inputFileId(RetrievalJson.string(root, "input_file_id"))
                .outputFileId(RetrievalJson.string(root, "output_file_id"))
                .errorFileId(RetrievalJson.string(root, "error_file_id"))
                .resultsUrl(RetrievalJson.string(root, "results_url"))
                .endpoint(RetrievalJson.string(root, "endpoint"))
                .completionWindow(RetrievalJson.string(root, "completion_window"))
                .createdAtMillis(RetrievalJson.millis(root, "created_at", "createdAt"))
                .expiresAtMillis(RetrievalJson.millis(root, "expires_at", "expiresAt"));
        if (anthropic) {
            job.status(statusFromAnthropic(RetrievalJson.string(root, "processing_status")));
            JSONObject counts = RetrievalJson.object(root, "request_counts");
            if (counts != null) {
                int succeeded = orZero(RetrievalJson.integer(counts, "succeeded"));
                int errored = orZero(RetrievalJson.integer(counts, "errored"));
                int canceled = orZero(RetrievalJson.integer(counts, "canceled"));
                int expired = orZero(RetrievalJson.integer(counts, "expired"));
                int processing = orZero(RetrievalJson.integer(counts, "processing"));
                job.requestCounts(succeeded + errored + canceled + expired + processing,
                        succeeded, errored + expired);
            }
        } else {
            job.status(statusFromOpenAi(RetrievalJson.string(root, "status")));
            JSONObject counts = RetrievalJson.object(root, "request_counts");
            if (counts != null) {
                job.requestCounts(
                        orZero(RetrievalJson.integer(counts, "total")),
                        orZero(RetrievalJson.integer(counts, "completed")),
                        orZero(RetrievalJson.integer(counts, "failed")));
            }
        }
        errors(root, job);
        return job.build();
    }

    private static void errors(@NonNull JSONObject root, @NonNull YoBatchJob.Builder job) {
        JSONArray errors = RetrievalJson.array(root, "errors");
        if (errors == null) {
            return;
        }
        for (int i = 0; i < errors.length(); i++) {
            Object entry = errors.opt(i);
            if (entry instanceof JSONObject object) {
                String message = RetrievalJson.string(object, "message", "code", "type");
                job.addError(message == null ? object.toString() : message);
            } else if (entry instanceof String text) {
                job.addError(text);
            }
        }
    }

    @NonNull
    public static List<YoBatchJob> decodeList(@NonNull String body, boolean anthropic) {
        JSONObject root = RetrievalJson.parseObject(body, "Batch list");
        JSONArray data = RetrievalJson.array(root, "data");
        if (data == null) {
            throw new YoAiProtocolException("Batch list has no data array", body);
        }
        List<YoBatchJob> jobs = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry != null) {
                jobs.add(decodeJob(entry.toString(), anthropic));
            }
        }
        return jobs;
    }

    @NonNull
    public static List<YoBatchResult> decodeResultsJsonl(@NonNull String jsonl) {
        List<YoBatchResult> results = new ArrayList<>();
        for (String line : jsonl.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            JSONObject entry;
            try {
                entry = new JSONObject(trimmed);
            } catch (JSONException exception) {
                throw new YoAiProtocolException("Batch result line is not JSON", trimmed, exception);
            }
            String customId = RetrievalJson.string(entry, "custom_id", "customId");
            if (customId == null) {
                continue;
            }
            JSONObject response = RetrievalJson.object(entry, "response");
            Integer statusCode = response == null
                    ? RetrievalJson.integer(entry, "status_code")
                    : RetrievalJson.integer(response, "status_code");
            String body = null;
            if (response != null) {
                Object payload = response.opt("body");
                body = payload == null || payload == JSONObject.NULL ? null : String.valueOf(payload);
            } else {
                body = RetrievalJson.string(entry, "body");
            }
            String error = errorMessage(entry);
            results.add(new YoBatchResult(customId, statusCode, body, error));
        }
        return results;
    }

    @Nullable
    private static String errorMessage(@NonNull JSONObject entry) {
        Object raw = entry.opt("error");
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof JSONObject object) {
            String message = RetrievalJson.string(object, "message", "code", "type");
            return message == null ? object.toString() : message;
        }
        return String.valueOf(raw);
    }

    @NonNull
    public static YoBatchStatus statusFromOpenAi(@Nullable String status) {
        if (status == null) {
            return YoBatchStatus.UNKNOWN;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "validating" -> YoBatchStatus.VALIDATING;
            case "in_progress" -> YoBatchStatus.IN_PROGRESS;
            case "finalizing" -> YoBatchStatus.FINALIZING;
            case "completed" -> YoBatchStatus.COMPLETED;
            case "failed" -> YoBatchStatus.FAILED;
            case "expiring" -> YoBatchStatus.EXPIRING;
            case "expired" -> YoBatchStatus.EXPIRED;
            case "cancelling", "canceling" -> YoBatchStatus.CANCELLING;
            case "cancelled", "canceled" -> YoBatchStatus.CANCELLED;
            default -> YoBatchStatus.UNKNOWN;
        };
    }

    @NonNull
    public static YoBatchStatus statusFromAnthropic(@Nullable String status) {
        if (status == null) {
            return YoBatchStatus.UNKNOWN;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "in_progress" -> YoBatchStatus.IN_PROGRESS;
            case "canceling", "cancelling" -> YoBatchStatus.CANCELLING;
            case "canceled", "cancelled" -> YoBatchStatus.CANCELLED;
            case "ended" -> YoBatchStatus.COMPLETED;
            default -> YoBatchStatus.UNKNOWN;
        };
    }

    private static int orZero(@Nullable Integer value) {
        return value == null ? 0 : value;
    }
}
