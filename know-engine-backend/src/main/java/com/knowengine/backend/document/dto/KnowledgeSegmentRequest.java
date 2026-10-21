package com.knowengine.backend.document.dto;

import com.knowengine.backend.document.constant.SegmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识片段新增、更新请求，不接收数据库主键。
 *
 * <p>PUT 更新时需重新提交文本、所属文档ID和顺序；可选字段省略或传 NULL 时保留数据库原值。
 */
@Data
public class KnowledgeSegmentRequest {

    /**
     * 片段文本内容，新增和更新时均必填。
     */
    @NotBlank
    private String text;

    /**
     * 分片ID。
     */
    @Size(max = 255)
    private String chunkId;

    /**
     * 片段元数据，按字符串提交和存储。
     */
    @Size(max = 2048)
    private String metadata;

    /**
     * 所属文档ID，必须指向已存在的知识文档。
     */
    @NotNull
    @Positive
    private Long documentId;

    /**
     * 片段在所属文档中的顺序，允许从 0 开始。
     */
    @NotNull
    @Min(0)
    private Integer chunkOrder;

    /**
     * 向量存储中的嵌入ID。
     */
    @Size(max = 255)
    private String embeddingId;

    /**
     * 片段存储状态；新增未传时默认为 STORED，更新未传时保留原状态。
     */
    private SegmentStatus status;

    /**
     * 是否跳过嵌入生成：0 不跳过，1 跳过；新增默认为 0，更新未传时保留原值。
     */
    @Min(0)
    @Max(1)
    private Integer skipEmbedding;
}
