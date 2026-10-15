package com.yovo.lib_ai.internal.responses;

import com.yovo.lib_ai.internal.json.Jsons;

import org.json.JSONObject;

/**
 * 一个 Responses 事件对当前这一轮生成意味着继续、结束、失败，还是被 steer 截断后仍占着通道。
 */
public final class ResponsesTurn {

    public enum Kind {
        CONTINUE,
        COMPLETE,
        FAIL,
        STEERED
    }

    private ResponsesTurn() {
    }

    public static Kind classify(JSONObject event) {
        String type = event.optString("type");
        if (ResponsesEvents.FAILED.equals(type)
                || ResponsesEvents.ERROR.equals(type)
                || ResponsesEvents.STEER_FAILED.equals(type)) {
            return Kind.FAIL;
        }
        if (ResponsesEvents.INCOMPLETE.equals(type) && steered(event)) {
            return Kind.STEERED;
        }
        if (ResponsesEvents.COMPLETED.equals(type) || ResponsesEvents.INCOMPLETE.equals(type)) {
            return Kind.COMPLETE;
        }
        return Kind.CONTINUE;
    }

    public static String errorMessage(JSONObject event) {
        JSONObject error = Jsons.optObject(event, "error");
        if (error != null) {
            String message = Jsons.optString(error, "message");
            if (message != null) {
                return message;
            }
        }
        JSONObject response = Jsons.optObject(event, "response");
        if (response != null) {
            JSONObject responseError = Jsons.optObject(response, "error");
            if (responseError != null && Jsons.optString(responseError, "message") != null) {
                return Jsons.optString(responseError, "message");
            }
            String status = Jsons.optString(response, "status");
            if (status != null) {
                return "Response " + status;
            }
        }
        String message = Jsons.optString(event, "message");
        return message == null ? event.optString("type", ResponsesEvents.ERROR) : message;
    }

    private static boolean steered(JSONObject event) {
        JSONObject snapshot = Jsons.optObject(event, "response");
        JSONObject details = snapshot == null ? null : Jsons.optObject(snapshot, "incomplete_details");
        return details != null && "steered".equals(Jsons.optString(details, "reason"));
    }
}
