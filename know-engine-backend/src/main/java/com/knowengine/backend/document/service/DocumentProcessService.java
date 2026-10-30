package com.knowengine.backend.document.service;

import com.knowengine.backend.document.dto.DocumentSplitParam;
import com.knowengine.backend.document.dto.DocumentUploadParam;
import com.knowengine.backend.document.entity.KnowledgeDocument;

import java.io.IOException;

/**
 * 文档处理服务接口
 * 负责文档的业务流程处理：上传、转换、分段、向量化
 */
public interface DocumentProcessService {

    /**
     * 上传文档处理
     *
     * @param documentUploadParam 上传参数
     * @param uploadUser          上传用户
     * @return 处理的内容
     */
    KnowledgeDocument upload(DocumentUploadParam documentUploadParam, String uploadUser) throws IOException;

    /**
     * 切分文档
     *
     * @param document 文档
     * @param documentSplitParam 切分参数
     * @return 切分数量
     */
    Integer split(KnowledgeDocument document, DocumentSplitParam documentSplitParam);

    // todo
    boolean embedAndStore();
}
