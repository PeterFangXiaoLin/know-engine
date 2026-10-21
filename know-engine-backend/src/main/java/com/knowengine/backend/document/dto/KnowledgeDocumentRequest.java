package com.knowengine.backend.document.dto;

import com.knowengine.backend.document.constant.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 知识文档新增、更新请求，不接收数据库主键。
 *
 * <p>PUT 更新时需重新提交标题；可选字段省略或传 NULL 时保留数据库原值。
 */
@Data
public class KnowledgeDocumentRequest {

    /**
     * 文档标题，新增和更新时均必填。
     */
    @NotBlank
    @Size(max = 1024)
    private String docTitle;

    /**
     * 上传用户。
     */
    @Size(max = 255)
    private String uploadUser;

    /**
     * 原始文档URL。
     */
    @Size(max = 2048)
    private String docUrl;

    /**
     * 转换后的文档URL。
     */
    @Size(max = 2048)
    private String convertedDocUrl;

    /**
     * 文档失效日期，请求格式为 yyyy-MM-dd。
     */
    private LocalDate expireDate;

    /**
     * 文档处理状态；新增未传时默认为 INIT，更新未传时保留原状态。
     */
    private DocumentStatus status;

    /**
     * 文档可见范围。
     */
    @Size(max = 1024)
    private String accessibleBy;

    /**
     * 扩展字段，以字符串形式提交 JSON 内容。
     */
    private String extension;
}
