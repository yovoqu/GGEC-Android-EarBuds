package com.ggec.earbuds.data.ai.store;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 以文本文档为单位的存储。名字用 "/" 分目录，例如 {@code conversations/ab12.json}。
 * 写入必须是原子的：读到的要么是旧内容，要么是新内容，不会是半截。
 */
public interface AiDocumentStore {

    @Nullable
    String read(String name);

    void write(String name, String content);

    void delete(String name);

    /**
     * 目录下的文档名（不含目录前缀），目录不存在返回空列表。
     */
    List<String> list(String directory);
}
