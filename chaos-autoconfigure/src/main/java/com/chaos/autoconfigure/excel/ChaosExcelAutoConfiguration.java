package com.chaos.autoconfigure.excel;

import com.chaos.core.importing.ImportSourceFactory;
import com.chaos.excel.support.ExcelImportSourceFactory;
import com.chaos.excel.support.ExcelImportSupport;
import com.chaos.web.http.ExcelImportUploads;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Excel 导入导出能力自动装配。
 *
 * <p>仅在 classpath 存在 chaos-excel 时注册暂存端口默认实现；只有同时存在 chaos-web 时才注册
 * MultipartFile 上传适配器。业务项目可通过自定义同类型 Bean 覆盖默认实现。</p>
 */
@AutoConfiguration
@ConditionalOnClass(ExcelImportSupport.class)
public class ChaosExcelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ImportSourceFactory.class)
    public ImportSourceFactory excelImportSourceFactory() {
        return new ExcelImportSourceFactory();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ExcelImportUploads.class)
    static class UploadConfiguration {

        @Bean
        @ConditionalOnMissingBean(ExcelImportUploads.class)
        ExcelImportUploads excelImportUploads(ImportSourceFactory sourceFactory) {
            return new ExcelImportUploads(sourceFactory);
        }
    }
}
