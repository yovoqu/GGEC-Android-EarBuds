package com.yovo.lib_ai.internal.responses;

/**
 * Responses 线上事件名。客户端命令和服务端事件都在这里，折叠和通道判定共用。
 */
public final class ResponsesEvents {

    public static final String CREATE = "response.create";
    public static final String STEER = "response.steer";
    public static final String OUTPUT_ITEM_ADDED = "response.output_item.added";
    public static final String OUTPUT_ITEM_DONE = "response.output_item.done";
    public static final String CONTENT_PART_ADDED = "response.content_part.added";
    public static final String OUTPUT_TEXT_DELTA = "response.output_text.delta";
    public static final String OUTPUT_TEXT_DONE = "response.output_text.done";
    public static final String FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    public static final String FUNCTION_CALL_ARGUMENTS_DONE = "response.function_call_arguments.done";
    public static final String REASONING_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
    public static final String REASONING_SUMMARY_TEXT_DONE = "response.reasoning_summary_text.done";
    public static final String REFUSAL_DELTA = "response.refusal.delta";
    public static final String REFUSAL_DONE = "response.refusal.done";
    public static final String CREATED = "response.created";
    public static final String IN_PROGRESS = "response.in_progress";
    public static final String COMPLETED = "response.completed";
    public static final String FAILED = "response.failed";
    public static final String INCOMPLETE = "response.incomplete";
    public static final String STEER_FAILED = "response.steer.failed";
    public static final String ERROR = "error";

    public static final String ITEM_MESSAGE = "message";
    public static final String ITEM_FUNCTION_CALL = "function_call";
    public static final String ITEM_REASONING = "reasoning";
    public static final String PART_OUTPUT_TEXT = "output_text";
    public static final String PART_REFUSAL = "refusal";
    public static final String PART_SUMMARY_TEXT = "summary_text";

    private ResponsesEvents() {
    }
}
