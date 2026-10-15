package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

public interface YoResponseStreamListener {

    void onEvent(@NonNull YoResponseStreamEvent event);

    void onComplete(@NonNull YoResponseObject response);

    void onFailure(@NonNull YoAiException error);
}
