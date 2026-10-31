package com.yovo.lib_ai.realtime;

/**
 * 模型这一轮用声音回答、用文字回答，还是两者都要。
 */
public enum YoRealtimeOutputModality {
    AUDIO("audio"),
    TEXT("text");

    private final String wireName;

    YoRealtimeOutputModality(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
