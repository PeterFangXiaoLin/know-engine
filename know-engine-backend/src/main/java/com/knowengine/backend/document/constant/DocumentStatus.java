package com.knowengine.backend.document.constant;

/**
 * 文档处理状态，数据库和 JSON 接口均使用枚举名称表示状态。
 */
public enum DocumentStatus {
    /**
     * 初始状态
     */
    INIT,

    /**
     * 上传完成
     */
    UPLOADED,

    /**
     * 转换中
     */
    CONVERTING,

    /**
     * 转换完成
     */
    CONVERTED,

    /**
     * 分块完成
     */
    CHUNKED,

    /**
     * 向量存储完成
     */
    VECTOR_STORED,

    /**
     * 存储完成（不需要向量存储的使用这个状态）
     */
    STORED;
}
