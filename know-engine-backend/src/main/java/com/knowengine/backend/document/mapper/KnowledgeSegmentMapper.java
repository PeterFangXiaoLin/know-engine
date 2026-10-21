package com.knowengine.backend.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowengine.backend.document.entity.KnowledgeSegment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识片段数据访问，字段映射和基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface KnowledgeSegmentMapper extends BaseMapper<KnowledgeSegment> {
}
