package com.chaos.core.file;

/**
 * 可下载附件契约，供应用层导出结果和 Web 层统一转换为下载响应。
 *
 * <p>实现类负责提供文件名、媒体类型和完整内容，不得依赖 Spring HTTP 类型。</p>
 */
public interface AttachmentFile {

    /**
     * 返回客户端保存附件时使用的文件名。
     *
     * @return 包含扩展名的下载文件名
     */
    String name();

    /**
     * 返回文件内容对应的互联网媒体类型。
     *
     * @return 用于 HTTP {@code Content-Type} 响应头的媒体类型
     */
    String contentType();

    /**
     * 返回导出文件的完整二进制内容。
     *
     * @return 文件字节；具体实现负责约定是否返回防御性副本
     */
    byte[] content();
}
