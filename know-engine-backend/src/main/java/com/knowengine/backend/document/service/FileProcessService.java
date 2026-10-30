package com.knowengine.backend.document.service;

import com.knowengine.backend.document.constant.FileType;
import com.knowengine.backend.document.constant.KnowledgeBaseType;
import com.knowengine.backend.document.entity.KnowledgeDocument;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件处理服务
 */
public interface FileProcessService {

    /**
     * 处理文档转换
     * 1. 从输入流读取文件内容
     * 2. 调用文档解析接口转换格式
     * 3. 转换后的文档保存在MinIO上
     * 4. 更新文档状态
     *
     * @param document 文档对象
     * @param inputStream 文件输入流，由调用方关闭
     * @return 转换后的文档在 MinIO 中的URL（convertedDocUrl），如果不涉及转换则返回 null
     * @throws IOException 文档解析或转换结果保存失败
     */
    String processDocument(KnowledgeDocument document, InputStream inputStream) throws IOException;

    /**
     * 判断是否支持该文件
     */
    boolean supports(FileType fileType, KnowledgeBaseType  knowledgeBaseType);
}
