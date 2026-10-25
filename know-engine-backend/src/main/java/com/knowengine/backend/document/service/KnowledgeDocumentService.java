package com.knowengine.backend.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.knowengine.backend.document.dto.KnowledgeDocumentQuery;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;

/**
 * 知识文档业务服务，在通用 CRUD 基础上处理默认状态、更新语义和关联片段删除。
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 创建文档，未指定状态时使用 INIT，返回含数据库自增ID的文档。
     *
     * @param request 文档内容和可选状态
     * @return 已保存的文档
     */
    KnowledgeDocument createDocument(KnowledgeDocumentRequest request);

    /**
     * 查询文档详情，记录不存在时抛出 HTTP 404 异常。
     *
     * @param docId 文档ID
     * @return 文档详情
     */
    KnowledgeDocument getDocument(Long docId);

    /**
     * 按标题、上传用户和状态筛选文档，结果按文档ID倒序分页。
     *
     * @param query 分页参数及筛选条件
     * @return 文档分页结果
     */
    IPage<KnowledgeDocument> pageDocuments(KnowledgeDocumentQuery query);

    /**
     * 更新文档内容，可选字段未指定时保留数据库原值。
     *
     * @param docId 文档ID
     * @param request 文档更新内容
     * @return 更新后的文档
     */
    KnowledgeDocument updateDocument(Long docId, KnowledgeDocumentRequest request);

    /**
     * 在同一事务中物理删除文档及其全部片段，仅处理关系型数据库记录。
     *
     * @param docId 文档ID
     */
    void deleteDocument(Long docId);

    /**
     * 向量化并存储
     *
     * @param docId docId
     * @return 是否成功
     */
    boolean embeddingAndStore(Long docId);
}
