package com.knowengine.backend.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowengine.backend.document.constant.SegmentStatus;
import com.knowengine.backend.document.dto.KnowledgeSegmentQuery;
import com.knowengine.backend.document.dto.KnowledgeSegmentRequest;
import com.knowengine.backend.document.entity.KnowledgeSegment;
import com.knowengine.backend.document.mapper.KnowledgeDocumentMapper;
import com.knowengine.backend.document.mapper.KnowledgeSegmentMapper;
import com.knowengine.backend.document.service.KnowledgeSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识片段业务实现，写入前锁定所属文档，防止与文档删除并发时产生孤立片段。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSegmentServiceImpl extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegment>
        implements KnowledgeSegmentService {

    private final KnowledgeDocumentMapper documentMapper;

    /**
     * 在所属文档下保存片段，默认标记为关系型数据库已存储。
     *
     * @param request 片段内容和所属文档信息
     * @return 已保存的片段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeSegment createSegment(KnowledgeSegmentRequest request) {
        // 1. 检查并锁定所属文档，防止片段写入期间父文档被删除。
        lockDocument(request.getDocumentId());

        // 2. 构建片段；关系型存储成功后的初始状态为 STORED，默认参与后续嵌入生成。
        KnowledgeSegment segment = new KnowledgeSegment();
        BeanUtils.copyProperties(request, segment);
        if (segment.getStatus() == null) {
            segment.setStatus(SegmentStatus.STORED);
        }
        if (segment.getSkipEmbedding() == null) {
            segment.setSkipEmbedding(0);
        }

        // 3. 保存片段，并由数据库回填自增ID。
        save(segment);
        return segment;
    }

    /**
     * 查询片段详情，不存在时返回 HTTP 404。
     *
     * @param id 片段ID
     * @return 片段详情
     */
    @Override
    public KnowledgeSegment getSegment(Long id) {
        KnowledgeSegment segment = getById(id);
        if (segment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识片段不存在: " + id);
        }
        return segment;
    }

    /**
     * 根据可选条件分页查询片段，状态使用枚举进行精确匹配。
     *
     * @param query 分页参数及筛选条件
     * @return 按文档ID、片段顺序和主键升序排列的分页结果
     */
    @Override
    public IPage<KnowledgeSegment> pageSegments(KnowledgeSegmentQuery query) {
        return page(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<KnowledgeSegment>lambdaQuery()
                        .eq(query.getDocumentId() != null, KnowledgeSegment::getDocumentId, query.getDocumentId())
                        .eq(StringUtils.hasText(query.getChunkId()), KnowledgeSegment::getChunkId, query.getChunkId())
                        .eq(query.getStatus() != null, KnowledgeSegment::getStatus, query.getStatus())
                        .eq(query.getSkipEmbedding() != null, KnowledgeSegment::getSkipEmbedding, query.getSkipEmbedding())
                        .orderByAsc(KnowledgeSegment::getDocumentId)
                        .orderByAsc(KnowledgeSegment::getChunkOrder)
                        .orderByAsc(KnowledgeSegment::getId));
    }

    /**
     * 更新片段及枚举状态，并检查目标文档是否存在。
     *
     * @param id 片段ID
     * @param request 片段更新内容
     * @return 数据库中实际保存的片段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeSegment updateSegment(Long id, KnowledgeSegmentRequest request) {
        // 1. 锁定目标文档并读取片段，使片段迁移与目标文档删除互斥。
        lockDocument(request.getDocumentId());
        KnowledgeSegment segment = getSegment(id);

        // 2. 复制同类型的枚举字段；未提交的可选字段按 MyBatis-Plus 默认策略保留原值。
        BeanUtils.copyProperties(request, segment);
        if (!updateById(segment)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识片段不存在: " + id);
        }

        // 3. 返回实际保存结果，包含数据库中保留的状态和其他可选字段。
        return getSegment(id);
    }

    /**
     * 删除指定片段，不存在时返回 HTTP 404。
     *
     * @param id 片段ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSegment(Long id) {
        if (!removeById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识片段不存在: " + id);
        }
    }

    /**
     * 在当前事务内检查目标文档并加排他行锁；目标文档不存在时拒绝片段写入。
     *
     * @param docId 文档ID
     */
    private void lockDocument(Long docId) {
        if (documentMapper.selectIdForUpdate(docId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "所属知识文档不存在: " + docId);
        }
    }
}
