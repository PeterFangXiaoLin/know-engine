package com.knowengine.backend.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowengine.backend.document.constant.SegmentStatus;
import lombok.Data;

/**
 * 知识片段，对应 knowledge_segment 表，保存文档分块后的文本及嵌入信息。
 */
@Data
@TableName("knowledge_segment")
public class KnowledgeSegment {

    /**
     * 片段ID，数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 片段文本内容，对应 LONGTEXT 字段。
     */
    private String text;

    /**
     * 分片ID，与数据库自增主键 id 区分。
     */
    private String chunkId;

    /**
     * 片段元数据，按字符串保存。
     */
    private String metadata;

    /**
     * 所属文档ID，对应 knowledge_document 表的 doc_id。
     */
    private Long documentId;

    /**
     * 片段在所属文档中的顺序，分页查询时按此字段升序排列。
     */
    private Integer chunkOrder;

    /**
     * 嵌入ID，用于关联向量存储中的嵌入记录。
     */
    private String embeddingId;

    /**
     * 片段存储状态：STORED（关系型数据库已存储）、VECTOR_STORED（向量已存储），允许为空。
     */
    private SegmentStatus status;

    /**
     * 是否跳过嵌入生成：0 表示不跳过，1 表示跳过，允许为空。
     */
    private Integer skipEmbedding;
}
