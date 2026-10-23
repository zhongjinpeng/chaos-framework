package com.chaos.excel;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 已生成的 XLSX 文件契约，可写入调用方拥有的输出流。
 *
 * <p>实现类负责生成或保存文件内容，但不得关闭调用方传入的输出流。</p>
 */
public interface ExcelFile {

    /**
     * 返回规范化后的下载文件名。
     *
     * @return 已清理路径且带有 {@code .xlsx} 后缀的文件名
     */
    String filename();

    /**
     * 返回文件的媒体类型。
     *
     * @return XLSX 标准媒体类型
     */
    String contentType();

    /**
     * 将文件内容写入调用方拥有的输出流，方法不会关闭该输出流。
     *
     * @param output 目标输出流，生命周期由调用方管理
     * @throws IOException 目标输出流拒绝写入文件字节时抛出
     */
    void writeTo(OutputStream output) throws IOException;
}
