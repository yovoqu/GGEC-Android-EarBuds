package com.yovo.lib_ai.platform.deepseek;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatContentPart;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;

/**
 * DeepSeek 对 Chat 图片数量和 HTTP 图片地址长度的限制。
 */
public final class DeepSeekChatPolicy implements YoChatRequestPolicy {

    public static final int MAX_HTTP_URL_LENGTH = 8192;
    public static final int MAX_IMAGES = 600;
    public static final DeepSeekChatPolicy INSTANCE = new DeepSeekChatPolicy();

    @Override
    public void check(@NonNull YoChatCompletionRequest request) {
        int images = 0;
        for (YoChatMessage message : request.messages()) {
            YoChatContentPart[] parts = message.parts();
            if (parts == null) {
                continue;
            }
            for (YoChatContentPart part : parts) {
                if (part.kind() != YoChatContentPart.Kind.IMAGE_URL) {
                    continue;
                }
                images++;
                String url = part.text();
                if (url != null
                        && (url.startsWith("https://") || url.startsWith("http://"))
                        && url.length() > MAX_HTTP_URL_LENGTH) {
                    throw new IllegalArgumentException(
                            "image url must be at most " + MAX_HTTP_URL_LENGTH + " characters");
                }
            }
        }
        if (images > MAX_IMAGES) {
            throw new IllegalArgumentException("at most " + MAX_IMAGES + " images are allowed");
        }
    }
}
