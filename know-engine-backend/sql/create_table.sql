-- 知识文档表
CREATE TABLE `knowledge_document`
(
    `doc_id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `doc_title`         VARCHAR(1024) NOT NULL COMMENT '文档标题',
    `upload_user`       VARCHAR(255) NULL COMMENT '上传用户',
    `doc_url`           VARCHAR(2048) NULL COMMENT '文档URL',
    `converted_doc_url` VARCHAR(2048) NULL COMMENT '转换后的文档URL',
    `status`            VARCHAR(32)   NOT NULL COMMENT '状态: INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    `accessible_by`     VARCHAR(1024) NULL COMMENT '可见范围',
    `extension`         TEXT NULL COMMENT '扩展字段，保存JSON字符串',

    PRIMARY KEY (`doc_id`),
    -- 为状态字段添加索引，优化定时任务扫表性能
    INDEX               `idx_status` (`status`),
    -- 复合索引：状态+文档ID，优化分页查询性能
    INDEX               `idx_status_doc_id` (`status`, `doc_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT = '知识文档表';


-- 知识片段表
CREATE TABLE `knowledge_segment`
(
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '片段ID',
    `text`           LONGTEXT NOT NULL COMMENT '文本内容',
    `chunk_id`       VARCHAR(255) NULL COMMENT '分片ID',
    `metadata`       VARCHAR(2048) NULL COMMENT '元数据',
    `document_id`    BIGINT   NOT NULL COMMENT '所属文档ID',
    `chunk_order`    INT      NOT NULL COMMENT '顺序',
    `embedding_id`   VARCHAR(255) NULL COMMENT '嵌入ID',
    `status`         VARCHAR(255) NULL COMMENT '状态: STORED, VECTOR_STORED',
    `skip_embedding` INT NULL COMMENT '是否跳过嵌入生成',

    PRIMARY KEY (`id`),
    INDEX            `idx_segment_document_id` (`document_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT = '知识片段表';

ALTER TABLE `knowledge_document`
    ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `lock_version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除';

ALTER TABLE `knowledge_segment`
    ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `lock_version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除';

ALTER TABLE `knowledge_document`
    ADD COLUMN `description` VARCHAR(512) COMMENT '文档描述',
    ADD COLUMN `knowledge_base_type` VARCHAR(32) COMMENT '知识库类型：DOCUMENT_SEARCH, DATA_QUERY';