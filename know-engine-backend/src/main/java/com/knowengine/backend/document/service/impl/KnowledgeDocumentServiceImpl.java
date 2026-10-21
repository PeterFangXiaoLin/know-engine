package com.knowengine.backend.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.dto.KnowledgeDocumentQuery;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.entity.KnowledgeSegment;
import com.knowengine.backend.document.mapper.KnowledgeDocumentMapper;
import com.knowengine.backend.document.mapper.KnowledgeSegmentMapper;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识文档业务实现，通过父文档行锁协调文档修改、删除与片段写入。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    private final KnowledgeSegmentMapper segmentMapper;

    /**
     * 保存文档，使用枚举补齐未指定的初始状态。
     *
     * @param request 文档内容和可选状态
     * @return 已保存的文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createDocument(KnowledgeDocumentRequest request) {
        // 1. 构建文档并补齐初始状态，避免向非空状态列写入 NULL。
        KnowledgeDocument document = new KnowledgeDocument();
        BeanUtils.copyProperties(request, document);
        if (document.getStatus() == null) {
            document.setStatus(DocumentStatus.INIT);
        }

        // 2. 保存文档，并由数据库回填自增ID。
        save(document);
        return document;
    }

    /**
     * 查询文档详情，不存在时返回 HTTP 404。
     *
     * @param docId 文档ID
     * @return 文档详情
     */
    @Override
    public KnowledgeDocument getDocument(Long docId) {
        KnowledgeDocument document = getById(docId);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识文档不存在: " + docId);
        }
        return document;
    }

    /**
     * 根据可选条件分页查询文档，状态使用枚举进行精确匹配。
     *
     * @param query 分页参数及筛选条件
     * @return 按文档ID倒序排列的分页结果
     */
    @Override
    public IPage<KnowledgeDocument> pageDocuments(KnowledgeDocumentQuery query) {
        return page(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .like(StringUtils.hasText(query.getDocTitle()), KnowledgeDocument::getDocTitle, query.getDocTitle())
                        .eq(StringUtils.hasText(query.getUploadUser()), KnowledgeDocument::getUploadUser, query.getUploadUser())
                        .eq(query.getStatus() != null, KnowledgeDocument::getStatus, query.getStatus())
                        .orderByDesc(KnowledgeDocument::getDocId));
    }

    /**
     * 更新文档，未提交状态时沿用原枚举值。
     *
     * @param docId 文档ID
     * @param request 文档更新内容
     * @return 数据库中实际保存的文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument updateDocument(Long docId, KnowledgeDocumentRequest request) {
        // 1. 锁定并读取文档，避免与文档删除、片段写入并发冲突。
        lockDocument(docId);
        KnowledgeDocument document = getDocument(docId);

        // 2. 复制同类型的枚举字段，未提交状态时保留原状态。
        DocumentStatus previousStatus = document.getStatus();
        BeanUtils.copyProperties(request, document);
        if (document.getStatus() == null) {
            document.setStatus(previousStatus);
        }

        // 3. 更新后重新读取，返回数据库保留的可选字段，避免响应中的 NULL 与实际值不一致。
        updateById(document);
        return getDocument(docId);
    }

    /**
     * 在同一事务中删除文档及其片段，失败时整体回滚。
     *
     * @param docId 文档ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long docId) {
        // 1. 与片段写入共用父文档行锁，避免删除过程中产生孤立片段。
        lockDocument(docId);

        // 2. 删除所属片段，任一步失败都会由事务恢复已删除的数据。
        segmentMapper.delete(Wrappers.<KnowledgeSegment>lambdaQuery()
                .eq(KnowledgeSegment::getDocumentId, docId));

        // 3. 删除父文档，完成关系型数据库中的关联清理。
        removeById(docId);
    }

    /**
     * 在当前事务内检查文档并加排他行锁，锁在事务提交或回滚后释放。
     *
     * @param docId 文档ID
     */
    private void lockDocument(Long docId) {
        if (baseMapper.selectIdForUpdate(docId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识文档不存在: " + docId);
        }
    }
}
