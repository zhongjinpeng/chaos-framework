package com.chaos.web.http;

import com.chaos.core.importing.ImportSource;
import com.chaos.core.importing.ImportSourceFactory;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 把 Servlet 上传流交给导入适配器完成限额暂存和摘要计算。
 *
 * <p>Controller 只依赖本类和应用层 {@link ImportSource}，不直接接触 Excel 框架的暂存类型与选项。</p>
 */
public class ExcelImportUploads {

    /**
     * 框架默认的导入文件大小上限：10 MiB。
     */
    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    private final ImportSourceFactory sourceFactory;

    /**
     * 注入应用层导入暂存端口。
     *
     * @param sourceFactory 上传内容暂存和摘要计算能力
     */
    public ExcelImportUploads(ImportSourceFactory sourceFactory) {
        this.sourceFactory = sourceFactory;
    }

    /**
     * 使用框架默认上限暂存导入文件。
     *
     * @param upload HTTP multipart 文件
     * @return 可重复读取并在关闭时删除临时文件的导入来源
     * @throws IOException 上传流读取失败时抛出
     */
    public ImportSource stage(MultipartFile upload) throws IOException {
        return stage(upload, DEFAULT_MAX_BYTES);
    }

    /**
     * 校验上传元数据并以流式方式暂存导入文件；调用方负责关闭返回句柄。
     *
     * @param upload HTTP multipart 文件，不能为空且内容不能为空
     * @param maxBytes 允许的最大字节数，必须大于 0
     * @return 可重复读取并在关闭时删除临时文件的导入来源
     * @throws IOException 上传流读取失败时抛出
     * @throws IllegalArgumentException 文件为空或声明大小超过上限时抛出
     */
    public ImportSource stage(MultipartFile upload, long maxBytes) throws IOException {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("导入文件大小上限必须大于 0");
        }
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (upload.getSize() > maxBytes) {
            throw new IllegalArgumentException("上传文件超过允许大小");
        }
        try (InputStream input = upload.getInputStream()) {
            return sourceFactory.stage(
                    Objects.requireNonNull(upload.getOriginalFilename(), "上传文件名不能为空"),
                    input,
                    maxBytes);
        }
    }
}
