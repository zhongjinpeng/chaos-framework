package com.chaos.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 在目标文件所在目录暂存内容，全部写入成功后才发布文件名。
 */
final class AtomicFileWriter {
    private AtomicFileWriter() {}

    /**
     * 创建所需目录，将内容复制到同目录临时文件，再以原子移动发布；不支持原子移动时写入失败。
     *
     * @param target 调用方已校验目录归属、分配唯一文件名的目标路径
     * @param content 从当前位置复制到流结束，流由调用方关闭
     * @throws IOException 创建、复制或移动失败；清理临时文件的次要异常附在原始异常上
     */
    static void write(Path target, InputStream content) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | SecurityException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
