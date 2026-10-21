package com.knowengine.backend.document.dto;

import com.knowengine.backend.document.constant.DocumentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识文档分页查询条件，未提供的筛选字段不参与过滤。
 */
@Data
public class KnowledgeDocumentQuery {

    /**
     * 页码，从 1 开始，默认为第 1 页。
     */
    @Min(1)
    private long pageNum = 1;

    /**
     * 每页条数，默认 10 条，最多 100 条。
     */
    @Min(1)
    @Max(100)
    private long pageSize = 10;

    /**
     * 文档标题，模糊匹配。
     */
    @Size(max = 1024)
    private String docTitle;

    /**
     * 上传用户，精确匹配。
     */
    @Size(max = 255)
    private String uploadUser;

    /**
     * 文档处理状态，精确匹配。
     */
    private DocumentStatus status;
}
