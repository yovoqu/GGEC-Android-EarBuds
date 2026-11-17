package com.ggec.earbuds.data.store;

/**
 * 文档读取失败：内容不是合法 JSON、版本不符、必填字段缺失或枚举值未知。
 *
 * <p>这是数据层内部的技术失败；repository 负责把它归一成对应 feature 的错误分类
 * （例如 {@code AiFailureKind.STORAGE}），不把本类型泄漏到领域或界面。</p>
 */
public final class DocumentFormatException extends RuntimeException {

    private final String field;

    public DocumentFormatException(String field, String message) {
        super(message);
        this.field = field;
    }

    public DocumentFormatException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    /** 出错的字段路径（例如 {@code voice.providers.openai.models[2].id}），用于定位，不作为用户文案。 */
    public String field() {
        return field;
    }
}
