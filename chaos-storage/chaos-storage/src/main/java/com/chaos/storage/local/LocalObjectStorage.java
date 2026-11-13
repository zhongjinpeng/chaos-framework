package com.chaos.storage.local;

import com.chaos.core.digest.Sha256;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 服务端分配键的本地对象存储；统一路径边界、原子写入和流式摘要，不承担业务授权。
 */
public final class LocalObjectStorage {
    private final Path root;
    private final Pattern allowedKeys;

    /**
     * 创建存储适配器，存储根目录及允许的键格式由业务适配器固定配置。
     * @param root 可信本地根目录，可尚不存在
     * @param allowedKeys 业务允许的完整相对键格式，不能来自 HTTP 参数
     */
    public LocalObjectStorage(Path root, Pattern allowedKeys) {
        this.root = root.toAbsolutePath().normalize();
        this.allowedKeys = Objects.requireNonNull(allowedKeys);
    }

    /**
     * 以流式方式原子写入并返回实际大小和摘要，失败清理临时文件。
     * @param key 已由服务端生成并符合本存储格式的相对键
     * @param content 从当前位置读取，调用方负责关闭
     * @return 持久化字节的大小与 SHA-256
     * @throws IOException 文件写入失败时抛出
     */
    public StoredObject write(String key, InputStream content) throws IOException {
        MessageDigest digest = Sha256.newDigest();
        var input = new CountingInputStream(new DigestInputStream(content, digest));
        AtomicFileWriter.write(resolve(key), input);
        return new StoredObject(input.count, HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * 打开已授权的内部对象。
     * @param key 由业务数据库取得并经过归属校验的相对键
     * @return 调用方负责关闭的读取流
     * @throws IOException 对象不存在或无法读取时抛出
     */
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    /**
     * 删除已授权对象；对象不存在时幂等成功。
     * @param key 业务已确认不再使用或需要回滚清理的相对键
     * @throws IOException 删除失败时抛出
     */
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !allowedKeys.matcher(key).matches()) {
            throw new IllegalArgumentException("文件存储键不合法");
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("文件超出存储目录");
        }
        // 根目录的宿主挂载可使用符号链接；根目录内不接受链接跳转。
        for (Path current = file;
                current != null && !current.equals(root);
                current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("存储目录内不允许符号链接");
        }
        return file;
    }

    /**
     * 已写入对象的可信摘要。
     * @param size 实际写入字节数
     * @param sha256 小写 SHA-256
     */
    public record StoredObject(long size, String sha256) {}

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int size = in.read(buffer, offset, length);
            if (size > 0) count += size;
            return size;
        }
    }
}
