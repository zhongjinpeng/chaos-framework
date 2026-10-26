package com.chaos.core.importing;

import java.io.IOException;

/**
 * 应用层可见的导入文件来源。
 *
 * <p>上传文件在接口层完成大小、扩展名和内容校验后暂存为可重复读取的临时文件；应用层只依赖该契约，
 * 不依赖具体 Excel 框架的暂存句柄。实现负责在关闭时清理临时内容。</p>
 */
public interface ImportSource extends AutoCloseable {

    /**
     * 返回清除客户端本地路径后的文件名。
     *
     * @return 用于错误提示和批次记录的文件名
     */
    String fileName();

    /**
     * 返回原始文件字节的 SHA-256 小写十六进制值。
     *
     * @return 文件摘要，用于导入批次幂等判断
     */
    String sha256();

    /**
     * 返回文件字节数。
     *
     * @return 暂存文件大小
     */
    long size();

    /**
     * 删除暂存内容；重复调用不重复清理。
     *
     * @throws IOException 临时文件无法删除时抛出
     */
    @Override
    void close() throws IOException;
}
