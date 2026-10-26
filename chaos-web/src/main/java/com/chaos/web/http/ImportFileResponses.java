package com.chaos.web.http;

import com.chaos.core.file.AttachmentFile;
import com.chaos.core.importing.ImportResultView;

import org.springframework.http.ResponseEntity;

/**
 * 导入接口的统一 HTTP 响应适配。
 *
 * <p>所有导入接口遵循同一约定：存在错误文件时返回 XLSX 附件，否则返回业务 JSON。
 * Controller 不再重复判断附件分支或自行拼装响应头。</p>
 */
public final class ImportFileResponses {

    private ImportFileResponses() {}

    /**
     * 按导入结果返回错误文件附件或成功行数。
     *
     * @param outcome 应用层导入结果，不能为空
     * @return 存在错误文件时为附件响应，否则为成功行数 JSON
     */
    public static ResponseEntity<?> from(ImportResultView outcome) {
        return attachmentOrBody(outcome.errorFile(), outcome.successRows());
    }

    /**
     * 存在错误文件时返回附件，否则返回调用方提供的业务响应体。
     *
     * <p>适用于携带业务摘要的导入接口，例如委托档案导入返回资源类型、新增和更新数量。</p>
     *
     * @param errorFile 错误文件；为 {@code null} 时返回业务响应体
     * @param body 无错误时返回的 JSON 响应体
     * @return 附件响应或业务 JSON 响应
     */
    public static ResponseEntity<?> attachmentOrBody(AttachmentFile errorFile, Object body) {
        if (errorFile != null) {
            return AttachmentResponses.of(errorFile);
        }
        return ResponseEntity.ok(body);
    }
}
