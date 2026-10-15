package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

public interface YoMessageStreamListener {

    void onEvent(@NonNull YoMessageStreamEvent event);

    void onComplete(@NonNull YoMessage message);

    void onFailure(@NonNull YoAiException error);
}
