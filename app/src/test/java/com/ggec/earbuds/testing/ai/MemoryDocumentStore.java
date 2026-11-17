package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.data.store.DocumentStore;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentWriteException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 内存文档存储，可以按需让写入失败，用来演练存储出错的路径。 */
public final class MemoryDocumentStore implements DocumentStore {

    public final Map<String, String> documents = new TreeMap<>();
    /** 按文档名让写入失败：用来只打断设置文档、不打断密钥文档。 */
    public final java.util.Set<String> failFor = new java.util.TreeSet<>();
    public boolean failWrites;
    public int writes;

    @Override
    public Optional<String> read(String name) {
        return Optional.ofNullable(documents.get(name));
    }

    @Override
    public void write(String name, String content) {
        if (fails(name)) {
            throw new DocumentWriteException(name, "disk is full", null);
        }
        writes++;
        documents.put(name, content);
    }

    @Override
    public void delete(String name) {
        if (fails(name)) {
            throw new DocumentWriteException(name, "disk is full", null);
        }
        documents.remove(name);
    }

    private boolean fails(String name) {
        return failWrites || failFor.contains(name);
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
        names.sort(String::compareTo);
        return names;
    }
}
