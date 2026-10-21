package com.knowengine.backend.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.knowengine.backend.document.dto.KnowledgeSegmentQuery;
import com.knowengine.backend.document.dto.KnowledgeSegmentRequest;
import com.knowengine.backend.document.entity.KnowledgeSegment;

/**
 * 知识片段业务服务，负责片段 CRUD 及所属文档的存在性检查。
 */
public interface KnowledgeSegmentService extends IService<KnowledgeSegment> {

    /**
     * 在已存在的文档下创建片段，状态默认为 STORED，跳过嵌入标记默认为 0。
     *
     * @param request 片段内容和所属文档信息
     * @return 已保存的片段
     */
    KnowledgeSegment createSegment(KnowledgeSegmentRequest request);

    /**
     * 查询片段详情，记录不存在时抛出 HTTP 404 异常。
     *
     * @param id 片段ID
     * @return 片段详情
     */
    KnowledgeSegment getSegment(Long id);

    /**
     * 按文档、分片和嵌入状态筛选，结果按文档ID、片段顺序及主键升序分页。
     *
     * @param query 分页参数及筛选条件
     * @return 片段分页结果
     */
    IPage<KnowledgeSegment> pageSegments(KnowledgeSegmentQuery query);

    /**
     * 更新片段内容，可选字段未指定时保留原值，并检查目标文档是否存在。
     *
     * @param id 片段ID
     * @param request 片段更新内容
     * @return 更新后的片段
     */
    KnowledgeSegment updateSegment(Long id, KnowledgeSegmentRequest request);

    /**
     * 物理删除指定片段，记录不存在时抛出 HTTP 404 异常。
     *
     * @param id 片段ID
     */
    void deleteSegment(Long id);
}
