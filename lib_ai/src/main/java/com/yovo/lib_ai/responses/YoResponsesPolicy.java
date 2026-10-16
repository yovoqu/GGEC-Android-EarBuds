package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;

import org.json.JSONObject;

/**
 * 发送前由平台执行的 Responses 约束，以及主机专有字段的合并。
 */
public interface YoResponsesPolicy {

    void check(@NonNull YoResponseRequest request);

    void apply(@NonNull JSONObject body);

    void checkSteer(@NonNull String previousResponseId);

    @NonNull
    static YoResponsesPolicy none() {
        return None.INSTANCE;
    }

    final class None implements YoResponsesPolicy {
        static final None INSTANCE = new None();

        private None() {
        }

        @Override
        public void check(@NonNull YoResponseRequest request) {
        }

        @Override
        public void apply(@NonNull JSONObject body) {
        }

        @Override
        public void checkSteer(@NonNull String previousResponseId) {
        }
    }
}
