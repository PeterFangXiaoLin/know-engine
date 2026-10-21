package com.knowengine.backend.document.dto;

import com.knowengine.backend.document.constant.SegmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识片段分页查询条件，未提供的筛选字段不参与过滤。
 */
@Data
public class KnowledgeSegmentQuery {

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
     * 所属文档ID，用于查询指定文档下的片段。
     */
    @Positive
    private Long documentId;

    /**
     * 分片ID，精确匹配。
     */
    @Size(max = 255)
    private String chunkId;

    /**
     * 片段处理状态，精确匹配。
     */
    private SegmentStatus status;

    /**
     * 按是否跳过嵌入生成筛选：0 不跳过，1 跳过。
     */
    @Min(0)
    @Max(1)
    private Integer skipEmbedding;
}
