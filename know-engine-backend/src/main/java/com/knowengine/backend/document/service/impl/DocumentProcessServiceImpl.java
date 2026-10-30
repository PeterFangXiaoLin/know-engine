package com.knowengine.backend.document.service.impl;

import com.alibaba.fastjson2.JSON;
import com.google.common.base.Stopwatch;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.constant.FileType;
import com.knowengine.backend.document.constant.KnowledgeBaseType;
import com.knowengine.backend.document.constant.SegmentStatus;
import com.knowengine.backend.document.dto.DocumentSplitParam;
import com.knowengine.backend.document.dto.DocumentUploadParam;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.entity.KnowledgeSegment;
import com.knowengine.backend.document.service.*;
import com.knowengine.backend.document.util.FileTypeUtil;
import com.knowengine.backend.infra.lock.DistributeLock;
import com.knowengine.backend.infra.snowflake.SnowflakeIdGenerator;
import com.knowengine.backend.rag.constant.MetadataKeyConstant;
import com.knowengine.backend.rag.modules.splitter.DocumentSplitterFactory;
import com.knowengine.backend.rag.modules.splitter.ExcelSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DocumentProcessServiceImpl implements DocumentProcessService {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private KnowledgeSegmentService knowledgeSegmentService;

    @Autowired
    private FileProcessServiceFactory fileProcessServiceFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    @DistributeLock(scene = "document-upload", keyExpression = "#uploadUser", waitTime = 0)
    public KnowledgeDocument upload(DocumentUploadParam documentUploadParam, String uploadUser) throws IOException {
        // 1. 创建，保存到数据库
        KnowledgeDocument document = new KnowledgeDocument().create(documentUploadParam);
        // 上传到minio
        log.info("start to upload ...");
        MultipartFile file = documentUploadParam.file();
        String filename = file.getOriginalFilename();
        String fileUrl = null;
        try {
            fileUrl = fileStorageService.uploadFile(file, filename);
        } catch (Exception e) {
            log.error("文件上传失败");
            return null;
        }
        document.setDocUrl(fileUrl);
        boolean result = knowledgeDocumentService.save(document);
        Assert.isTrue(result, "文件上传失败");

        // 2.处理文档，获取转换后的url
        String convertedDocUrl = processFile(filename, file, document, fileUrl);

        // 3. 更新数据库文档信息
        document.setConvertedDocUrl(convertedDocUrl);
        result = knowledgeDocumentService.updateById(document);
        Assert.isTrue(result, "文档更新失败");

        return document;
    }

    private String processFile(String filename, MultipartFile file, KnowledgeDocument document, String fileUrl) throws IOException {
        String convertedDocUrl;
        FileProcessService fileProcessService = fileProcessServiceFactory.get(FileTypeUtil.getFileType(filename, file), document.getKnowledgeBaseType());
        // pdf word excel md
        if (fileProcessService != null) {
            // 文件处理器只返回结果URL，输入流在转换成功或失败后均由调用方关闭。
            try (InputStream inputStream = file.getInputStream()) {
                convertedDocUrl = fileProcessService.processDocument(document, inputStream);
            }
        } else {
            // 不是上面四类的，直接修改状态为转化完成
            DocumentStatus documentStatus = document.getKnowledgeBaseType() == KnowledgeBaseType.DOCUMENT_SEARCH
                    ? DocumentStatus.CONVERTED
                    : DocumentStatus.STORED;
            document.setStatus(documentStatus);
            convertedDocUrl = fileUrl;
        }
        return convertedDocUrl;
    }

    @Override
    @DistributeLock(scene = "document-split", keyExpression = "#document.docId", waitTime = 0)
    public Integer split(KnowledgeDocument document, DocumentSplitParam documentSplitParam) {
        // 1. 校验参数
        Assert.notNull(document, "文档不存在");
        Assert.notNull(document.getConvertedDocUrl(), "文档未转换完成");

        DocumentStatus documentStatus = document.getStatus();
        // 已切分
        if (documentStatus == DocumentStatus.CHUNKED) {
            // 查询并返回已切分的个数
            Long chunkedCount = knowledgeSegmentService.lambdaQuery()
                    .eq(KnowledgeSegment::getDocumentId, document.getDocId())
                    .eq(KnowledgeSegment::getSkipEmbedding, 0)
                    .count();
            return chunkedCount.intValue();
        }

        if (documentStatus != DocumentStatus.CONVERTED) {
            throw new RuntimeException("文档状态不为CONVERTED，无法完成切分");
        }

        // 2. 从 url 提取出 objectName
        String convertedDocUrl = document.getConvertedDocUrl();
        String objectName = extractObjectNameFromUrl(convertedDocUrl);
        Assert.notNull(objectName, "无法解析文档URL");

        // 下载文件
        List<TextSegment> segments;
        try (InputStream inputStream = fileStorageService.downloadFile(objectName)) {
            // 如果是 excel
            if (FileType.EXCEL == FileTypeUtil.getFileType(convertedDocUrl)
                    || FileType.CSV == FileTypeUtil.getFileType(convertedDocUrl)) {
                // excel 每行一个chunk
                ExcelSplitter splitter = new ExcelSplitter(documentSplitParam.chunkSize(), false);
                segments = splitter.split(inputStream.readAllBytes());
            } else {
                // 不是 excel
                DocumentSplitter splitter = DocumentSplitterFactory.getInstance(documentSplitParam);
                Assert.notNull(splitter, "未找到满足要求的切分器");
                // 流 转 string
                Document doc = Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                segments = splitter.split(doc);
            }
        } catch (Exception e) {
            throw new RuntimeException("下载文档失败: " + e.getMessage(), e);
        }

        // 4. 把 segment 转 成 knowledgesegment 保存到数据库
        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeSegment knowledgeSegment = new KnowledgeSegment();
            knowledgeSegment.setText(segment.text());
            // 设置分段ID，部分自定义的分段器中已经设置过chunk id，用于处理父子分段，其他的没有的这里单独生成一个
            if (segment.metadata().getString(MetadataKeyConstant.CHUNK_ID) != null) {
                knowledgeSegment.setChunkId(segment.metadata().getString(MetadataKeyConstant.CHUNK_ID));
            } else {
                knowledgeSegment.setChunkId(SnowflakeIdGenerator.getInstance().nextIdStr());
            }

            // 获取文档的 metadata
            Metadata metadata = segment.metadata();
            knowledgeSegment.setMetadata(enrichMetadata(document, metadata, knowledgeSegment));
            knowledgeSegment.setDocumentId(document.getDocId());
            knowledgeSegment.setChunkOrder(i);

            // 检查是否需要向量化（父chunk 不需要向量化）
            Integer skipEmbedding = metadata.getInteger(MetadataKeyConstant.SKIP_EMBEDDING);
            if (skipEmbedding != null && skipEmbedding == 1) {
                knowledgeSegment.setSkipEmbedding(1);
            } else {
                knowledgeSegment.setSkipEmbedding(0);
            }
            knowledgeSegment.setStatus(SegmentStatus.STORED);

            knowledgeSegments.add(knowledgeSegment);
        }

        // 5. 批量保存到数据库
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean saveResult = knowledgeSegmentService.saveBatch(knowledgeSegments);
        Assert.isTrue(saveResult, "保存知识片段失败");
        log.info("保存知识片段耗时: {}", stopwatch.elapsed().toMillis());

        int segmentCount = knowledgeSegments.size();

        // 6. 更新文档状态为 CHUNKED，并保存分段参数
//        boolean advanceResult = knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CHUNKED);
//        Assert.isTrue(advanceResult, "更新文档版本状态失败");

        // 发送文档已分段事件
        publishChunkedEvent(document, segmentCount);

        return segmentCount;
    }

    @Override
    public boolean embedAndStore() {
        return false;
    }

    /**
     * 从MinIO URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // URL格式: http://endpoint/bucketName/objectName
        int lastSlashIndex = url.lastIndexOf(bucketName) + bucketName.length();
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            return null;
        }
        return url.substring(lastSlashIndex + 1);
    }

    /**
     * 填充元数据
     *
     * @param document         文档信息
     * @param metadata         元数据
     * @param knowledgeSegment segment 信息
     * @return metadata
     */
    private String enrichMetadata(KnowledgeDocument document, Metadata metadata, KnowledgeSegment knowledgeSegment) {
        metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
        metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
        // 没有chunkId 则补充
        if (metadata.getString(MetadataKeyConstant.CHUNK_ID) == null) {
            metadata.put(MetadataKeyConstant.CHUNK_ID, knowledgeSegment.getChunkId());
        }

        Map<String, Object> metadataMap = metadata.toMap();
        metadataMap.put(MetadataKeyConstant.ACCESSIBLE_BY, document.getAccessibleBy());

        return JSON.toJSONString(metadataMap);
    }

    /**
     * 发送文档已分段事件
     */
    private void publishChunkedEvent(KnowledgeDocument document, int segmentCount) {
        log.info("发送文档CHUNKED事件，documentId: {}, segmentCount: {}", document.getDocId(), segmentCount);
//        DocumentChunkedEvent event = new DocumentChunkedEvent(this, document.getDocId(), document.getCurrentVersionId(), segmentCount);
//        eventPublisher.publishEvent(event);
    }

}
