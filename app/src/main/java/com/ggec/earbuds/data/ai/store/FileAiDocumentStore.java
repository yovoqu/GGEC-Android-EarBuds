package com.ggec.earbuds.data.ai.store;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 以目录为根的文档存储。写入先落到同目录的临时文件并同步到磁盘，再整体改名覆盖目标，
 * 进程在任何一步被杀，目标文件要么是旧内容，要么是新内容。
 */
public final class FileAiDocumentStore implements AiDocumentStore {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");
    private static final String TEMP_SUFFIX = ".tmp";

    private final File root;

    public FileAiDocumentStore(File root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Nullable
    @Override
    public String read(String name) {
        File file = resolve(name);
        if (!file.isFile()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AiStorageException("Failed to read " + name, failure);
        }
    }

    @Override
    public void write(String name, String content) {
        File target = resolve(name);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new AiStorageException("Cannot create directory for " + name);
        }
        File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            temp.delete();
            throw new AiStorageException("Failed to write " + name, failure);
        }
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(resolve(name).toPath());
        } catch (IOException failure) {
            throw new AiStorageException("Failed to delete " + name, failure);
        }
    }

    @Override
    public List<String> list(String directory) {
        File[] children = resolve(directory).listFiles(File::isFile);
        if (children == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (File child : children) {
            if (!child.getName().endsWith(TEMP_SUFFIX)) {
                names.add(child.getName());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private File resolve(String name) {
        if (!SAFE_NAME.matcher(name).matches() || Arrays.asList(name.split("/")).contains("..")) {
            throw new IllegalArgumentException("Unsafe document name: " + name);
        }
        return new File(root, name);
    }
}
