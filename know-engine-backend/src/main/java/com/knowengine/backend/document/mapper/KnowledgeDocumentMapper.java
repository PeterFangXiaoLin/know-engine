package com.knowengine.backend.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识文档数据访问，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 查询并锁定父文档行，使文档删除与片段写入互斥，调用方必须开启事务。
     *
     * @return 文档ID；文档不存在时返回 null
     */
    @Select("SELECT doc_id FROM knowledge_document WHERE doc_id = #{docId} FOR UPDATE")
    Long selectIdForUpdate(@Param("docId") Long docId);
}
