package com.chaos.excel.support;

import com.chaos.core.importing.ImportSource;
import com.chaos.core.importing.ImportSourceFactory;

import java.io.InputStream;

/**
 * 基于 Chaos Excel 暂存能力的导入来源工厂。
 *
 * <p>由自动装配注册为默认 {@link ImportSourceFactory}；业务项目可通过自定义同类型 Bean 覆盖。</p>
 */
public class ExcelImportSourceFactory implements ImportSourceFactory {

    @Override
    public ImportSource stage(String filename, InputStream input, long maxBytes) {
        return ExcelImportSupport.stage(filename, input, maxBytes);
    }
}
