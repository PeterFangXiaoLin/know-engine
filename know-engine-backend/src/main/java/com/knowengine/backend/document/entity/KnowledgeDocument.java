package com.knowengine.backend.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowengine.backend.document.constant.DocumentStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识文档，对应 knowledge_document 表，记录文档来源、可见范围和处理状态。
 */
@Getter
@Setter
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    /**
     * 文档ID，数据库自增主键。
     */
    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    /**
     * 文档标题。
     */
    private String docTitle;

    /**
     * 上传用户。
     */
    private String uploadUser;

    /**
     * 原始文档URL。
     */
    private String docUrl;

    /**
     * 转换后的文档URL。
     */
    private String convertedDocUrl;

    /**
     * 文档处理状态：INIT（初始化）、UPLOADED（已上传）、CONVERTING（转换中）、
     * CONVERTED（已转换）、CHUNKED（已分片）、VECTOR_STORED（向量已存储）。
     */
    private DocumentStatus status;

    /**
     * 文档可见范围。
     */
    private String accessibleBy;

    /**
     * 扩展字段，用于保存 JSON 字符串。
     */
    private String extension;
}
