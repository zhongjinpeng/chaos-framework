package com.chaos.core.importing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验文件级导入失败对用户展示的稳定提示。
 */
class ImportRejectedExceptionTests {

  @Test
  void rowLimitMessageShouldContainConfiguredLimit() {
    var exception =
        new ImportRejectedException(
            ImportRejectedException.Reason.ROW_LIMIT_EXCEEDED, "导入数据行数超过单次上限", 5_000);

    assertTrue(exception.userMessage().contains("5000"));
    assertTrue(exception.userMessage().contains("拆分文件"));
  }

  @Test
  void headerMismatchMessageShouldGuideToLatestTemplate() {
    var exception =
        new ImportRejectedException(
            ImportRejectedException.Reason.HEADER_MISMATCH, "导入文件表头与模板不一致");

    assertTrue(exception.userMessage().contains("表头"));
    assertTrue(exception.userMessage().contains("最新模板"));
  }
}
