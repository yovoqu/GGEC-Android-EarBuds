package com.yovo.lib_ai.internal.protocol;

/**
 * 三条线协议的路径。只在这里定义一次。
 */
public final class WirePaths {

    public static final String CHAT_COMPLETIONS = "/v1/chat/completions";
    public static final String RESPONSES = "/v1/responses";
    public static final String MESSAGES = "/v1/messages";

    private WirePaths() {
    }
}
