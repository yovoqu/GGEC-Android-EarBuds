package com.ggec.earbuds.data.store;

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
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 以目录为根的文档存储。写入先落到同目录临时文件并同步到磁盘，再整体改名覆盖目标。
 *
 * <p>本类是 APP 唯一的文件写入实现；台账、设置、会话与界面偏好都经它。</p>
 */
public final class FileDocumentStore implements DocumentStore {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");
    private static final String TEMP_SUFFIX = ".tmp";

    private final File root;

    public FileDocumentStore(File root) {
        this.root = Objects.requireNonNull(root, "root");
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("Cannot create document root " + root);
        }
    }

    @Override
    public Optional<String> read(String name) {
        File file = resolve(name);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new DocumentReadException(name, failure);
        }
    }

    @Override
    public void write(String name, String content) {
        Objects.requireNonNull(content, "content");
        File target = resolve(name);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new DocumentWriteException(name, "cannot create directory for " + name, null);
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
            throw new DocumentWriteException(name, "failed to write " + name, failure);
        }
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(resolve(name).toPath());
        } catch (IOException failure) {
            throw new DocumentWriteException(name, "failed to delete " + name, failure);
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
        if (name == null || !SAFE_NAME.matcher(name).matches()
                || Arrays.asList(name.split("/")).contains("..")) {
            throw new IllegalArgumentException("Unsafe document name: " + name);
        }
        return new File(root, name);
    }

    /** 读失败：文件存在但读不出来。 */
    public static final class DocumentReadException extends RuntimeException {
        public DocumentReadException(String name, Throwable cause) {
            super("failed to read " + name, cause);
        }
    }

    /** 写/删失败：目录建不出来、同步失败或改名失败。 */
    public static final class DocumentWriteException extends RuntimeException {
        private final String name;

        public DocumentWriteException(String name, String message, Throwable cause) {
            super(message, cause);
            this.name = name;
        }

        public String name() {
            return name;
        }
    }
}
