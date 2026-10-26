package com.chaos.core.importing;

import java.io.InputStream;

/**
 * 导入文件暂存端口，隔离 HTTP 上传与具体文件框架的临时文件、摘要和限额实现。
 *
 * <p>Interfaces 层只负责把上传流交给该端口；Application 层通过 {@link ImportSource} 使用结果，
 * Infrastructure 负责实际的临时文件写入和 SHA-256 计算。</p>
 */
public interface ImportSourceFactory {

    /**
     * 按字节上限暂存上传内容并计算摘要。
     *
     * @param filename 客户端文件名，可包含浏览器传入的本地路径
     * @param input 上传文件流，生命周期由调用方管理
     * @param maxBytes 允许的最大字节数，必须大于 0
     * @return 可重复读取、关闭时删除临时文件的导入来源
     * @throws com.chaos.core.importing.ImportRejectedException
     *     扩展名、大小、内容或暂存失败时抛出
     */
    ImportSource stage(String filename, InputStream input, long maxBytes);
}
