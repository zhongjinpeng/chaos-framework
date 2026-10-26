package com.chaos.excel.support;

import com.chaos.core.importing.ImportSource;
import com.chaos.excel.ExcelImports;

import java.io.IOException;

/**
 * 基于框架暂存能力的导入来源实现。
 *
 * <p>该适配器是唯一知道框架暂存句柄的地方：应用层只看到 {@link ImportSource}，基础设施实现
 * 通过 {@link #of(ImportSource)} 取回框架句柄后交给文件框架解析。</p>
 */
final class StagedImportSource implements ImportSource {

    private final ExcelImports.StagedFile stagedFile;

    private StagedImportSource(ExcelImports.StagedFile stagedFile) {
        this.stagedFile = stagedFile;
    }

    /**
     * 包装框架暂存句柄。
     *
     * @param stagedFile 已完成大小和摘要计算的暂存文件
     * @return 应用层导入来源
     */
    static StagedImportSource wrap(ExcelImports.StagedFile stagedFile) {
        return new StagedImportSource(stagedFile);
    }

    /**
     * 取回框架暂存句柄，仅供基础设施适配器解析文件时使用。
     *
     * @param source 应用层导入来源
     * @return 框架暂存句柄
     * @throws IllegalArgumentException 来源不是本适配器产生时抛出
     */
    static StagedImportSource of(ImportSource source) {
        if (source instanceof StagedImportSource staged) {
            return staged;
        }
        throw new IllegalArgumentException("不支持的导入来源类型: " + source.getClass().getName());
    }

    /**
     * 返回框架暂存句柄，仅供基础设施适配器解析文件时使用。
     *
     * @return 框架暂存句柄
     */
    ExcelImports.StagedFile stagedFile() {
        return stagedFile;
    }

    @Override
    public String fileName() {
        return stagedFile.filename();
    }

    @Override
    public String sha256() {
        return stagedFile.sha256();
    }

    @Override
    public long size() {
        return stagedFile.size();
    }

    @Override
    public void close() throws IOException {
        stagedFile.close();
    }
}
