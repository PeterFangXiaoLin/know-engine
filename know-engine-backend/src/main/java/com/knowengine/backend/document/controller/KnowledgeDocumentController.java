package com.knowengine.backend.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowengine.backend.document.dto.DocumentSplitParam;
import com.knowengine.backend.document.dto.DocumentUploadParam;
import com.knowengine.backend.document.dto.KnowledgeDocumentQuery;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.service.DocumentProcessService;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 知识文档管理接口，校验请求后交由业务服务处理。
 *
 * <p>新增返回 201，查询和更新返回 200，删除文档及其片段后返回 204。
 */
@RestController
@RequestMapping("/knowledge/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;

    private final DocumentProcessService documentProcessService;

    private final Tika tika;

    /**
     * 上传文件，PDF 同步解析为 Markdown，其他文件直接标记转换完成。
     *
     * @param file         文件
     * @param uploadUser   上传用户
     * @param accessibleBy 文件可见范围
     * @return 包含原始文件URL、转换后URL和最新状态的文档
     * @throws IOException 文档转换或文件读取失败
     */
    @PostMapping("/upload")
    public KnowledgeDocument uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("uploadUser") String uploadUser,
            @RequestParam("description") String description,
            @RequestParam("knowledgeBaseType") String knowledgeBaseType,
            @RequestParam(value = "accessibleBy", required = false) String accessibleBy) throws IOException {
        return documentProcessService.upload(new DocumentUploadParam(file, title, accessibleBy, description, knowledgeBaseType), uploadUser);
    }

    /**
     * 对文档进行切分
     * 注意：此方法为手动触发切分接口，正常流程由事件驱动自动执行
     *
     * @param documentId 文档ID
     * @return 切分后的片段数量
     */
    @PostMapping("/split/{documentId}")
    public Integer splitDocument(@PathVariable Long documentId,
                                 @RequestParam("splitType") String splitType,
                                 @RequestParam("chunkSize") Integer chunkSize,
                                 @RequestParam(value = "overlap", required = false) Integer overlap,
                                 @RequestParam(value = "regex", required = false) String regex,
                                 @RequestParam(value = "titleLevel", required = false) Integer titleLevel,
                                 @RequestParam(value = "separator", required = false) String separator) {
        KnowledgeDocument document = documentService.getById(documentId);
        return documentProcessService.split(document,
                new DocumentSplitParam(splitType, chunkSize, overlap, titleLevel, separator, regex));
    }



    /**
     * 查询文档详情。
     *
     * @param docId 文档ID
     * @return 文档详情
     */
    @GetMapping("/{docId}")
    public KnowledgeDocument get(@Positive @PathVariable Long docId) {
        return documentService.getDocument(docId);
    }

    /**
     * 按条件分页查询文档。
     *
     * @param query 分页参数和筛选条件
     * @return 文档分页结果
     */
    @GetMapping
    public IPage<KnowledgeDocument> page(@Valid @ModelAttribute KnowledgeDocumentQuery query) {
        return documentService.pageDocuments(query);
    }

    /**
     * 更新文档内容。
     *
     * @param docId   文档ID
     * @param request 更新内容
     * @return 更新后的文档
     */
    @PutMapping("/{docId}")
    public KnowledgeDocument update(@Positive @PathVariable Long docId,
                                    @Valid @RequestBody KnowledgeDocumentRequest request) {
        return documentService.updateDocument(docId, request);
    }

    /**
     * 删除文档及其片段记录。
     *
     * @param docId 文档ID
     * @return HTTP 204 响应
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@Positive @PathVariable Long docId) {
        documentService.deleteDocument(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 通过后缀名判断是否为 PDF 文件
     *
     * @param fileName 文件名
     * @return true 如果是 PDF 文件
     */
    private boolean isPdfFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * 通过 Apache Tika 检测文件内容类型判断是否为 PDF 文件
     *
     * @param file 上传的文件
     * @return true 如果是 PDF 文件
     */
    private boolean isPdfContent(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String mimeType = tika.detect(is);
            return "application/pdf".equals(mimeType);
        }
    }
}
