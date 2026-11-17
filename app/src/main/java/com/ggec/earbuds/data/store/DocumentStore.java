package com.ggec.earbuds.data.store;

import java.util.List;
import java.util.Optional;

/**
 * APP 内唯一的文档 IO 原语：以应用私有目录为根的 JSON/文本文档存取。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #read} 的“没有这份文档”用 {@link Optional#empty()} 表达，读失败用
 *       {@link DocumentFormatException} 表达；两者不混用。</li>
 *   <li>{@link #write} 必须先落同目录临时文件并同步，再原子改名覆盖目标；进程在任一步被杀，
 *       目标文件要么是旧内容，要么是新内容。</li>
 *   <li>{@link #list} 只列普通文件，不返回临时文件。</li>
 * </ul>
 *
 * <p>台账、设置、会话与界面偏好共用这一套实现；任何 feature 不得再自建文件写入或
 * {@code SharedPreferences}。</p>
 */
public interface DocumentStore {

    Optional<String> read(String name);

    void write(String name, String content);

    void delete(String name);

    List<String> list(String directory);
}
