package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 原子写内容生产者；抛出 IOException 表示写失败。
 */
public interface StreamWriter {

    void write(@NonNull OutputStream out) throws IOException;
}
