package com.knowengine.backend.document.constant;

/**
 * 片段存储状态，数据库和 JSON 接口均使用枚举名称表示状态。
 */
public enum SegmentStatus {
    /**
     * 关系型数据库存储完成
     */
    STORED,

    /**
     * 向量数据库存储完成（非必须）
     */
    VECTOR_STORED;
}
