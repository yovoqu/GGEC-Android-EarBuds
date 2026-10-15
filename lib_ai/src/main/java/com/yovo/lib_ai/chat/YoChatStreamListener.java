package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

public interface YoChatStreamListener {

    void onChunk(@NonNull YoChatChunk chunk);

    void onComplete(@NonNull YoChatCompletion completion);

    void onFailure(@NonNull YoAiException error);
}
