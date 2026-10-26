package com.chaos.autoconfigure.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.importing.ImportSourceFactory;
import com.chaos.web.http.ExcelImportUploads;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Excel 导入导出自动装配测试。
 */
class ChaosExcelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosExcelAutoConfiguration.class));

    /**
     * 同时存在 chaos-excel 与 chaos-web 时提供默认暂存和上传适配器。
     */
    @Test
    void shouldRegisterDefaultImportSourceFactoryAndUploadAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImportSourceFactory.class);
            assertThat(context).hasSingleBean(ExcelImportUploads.class);
        });
    }
}
