package com.ggec.earbuds.testing.ai;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.AiStorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 内存文档存储，可以按需让写入失败，用来演练存储出错的路径。 */
public final class MemoryDocumentStore implements AiDocumentStore {

    public final Map<String, String> documents = new TreeMap<>();
    public boolean failWrites;
    public int writes;

    @Nullable
    @Override
    public String read(String name) {
        return documents.get(name);
    }

    @Override
    public void write(String name, String content) {
        if (failWrites) {
            throw new AiStorageException("disk is full");
        }
        writes++;
        documents.put(name, content);
    }

    @Override
    public void delete(String name) {
        if (failWrites) {
            throw new AiStorageException("disk is full");
        }
        documents.remove(name);
    }

    @Override
    public List<String> list(String directory) {
        List<String> names = new ArrayList<>();
        String prefix = directory + "/";
        for (String name : documents.keySet()) {
            if (name.startsWith(prefix) && name.indexOf('/', prefix.length()) < 0) {
                names.add(name.substring(prefix.length()));
            }
        }
        return names;
    }
}
