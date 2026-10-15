package com.yovo.lib_common.system.file.internal.path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.internal.util.IoUtil;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AtomicFile 语义原子写：临时文件 + flush + FD sync + rename；失败清理 .new。
 *
 * <p>同一目标路径的并发写由内部短锁互斥。</p>
 */
public final class AtomicPathWriter {

    private static final ReentrantLock[] PATH_LOCKS = new ReentrantLock[32];

    static {
        for (int i = 0; i < PATH_LOCKS.length; i++) {
            PATH_LOCKS[i] = new ReentrantLock();
        }
    }

    private AtomicPathWriter() {
    }

    /**
     * 内容生产者；抛出 IOException 表示写失败。
     */
    public interface Writer {
        void write(@NonNull OutputStream out) throws IOException;
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull File target,
                                               @NonNull byte[] bytes) {
        return writeAtomic(target, out -> out.write(bytes));
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull File target,
                                               @NonNull Writer writer) {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            FileResult<File> ensured = PathFileStore.ensureDir(parent);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            return writeAtomicLocked(target, parent, writer);
        } finally {
            lock.unlock();
        }
    }

    @NonNull
    private static FileResult<Void> writeAtomicLocked(@NonNull File target,
                                                      @Nullable File parent,
                                                      @NonNull Writer writer) {
        File temp = new File(parent != null ? parent : new File("."),
                target.getName() + ".new");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            writer.write(out);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            IoUtil.deleteQuietly(temp);
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "atomic write failed: " + target, e);
        }
        if (!IoUtil.moveReplace(temp, target)) {
            IoUtil.deleteQuietly(temp);
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot replace target: " + target);
        }
        return FileResult.ok(null);
    }

    @NonNull
    private static ReentrantLock lockFor(@NonNull File target) {
        int index = (target.getAbsolutePath().hashCode() & Integer.MAX_VALUE) % PATH_LOCKS.length;
        return PATH_LOCKS[index];
    }
}
