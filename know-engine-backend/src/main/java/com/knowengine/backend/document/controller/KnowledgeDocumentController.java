package com.knowengine.backend.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.dto.KnowledgeDocumentQuery;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.service.FileStorageService;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * 知识文档管理接口，校验请求后交由业务服务处理。
 *
 * <p>新增返回 201，查询和更新返回 200，删除文档及其片段后返回 204。
 */
@RestController
@RequestMapping("/api/knowledge/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;

    private final FileStorageService fileStorageService;

    /**
     * 上传文件
     *
     * @param file 文件
     * @param uploadUser 上传用户
     * @param accessibleBy 文件可见范围
     * @return document
     */
    @PostMapping("/upload")
    public KnowledgeDocument uploadFile(
            @RequestParam("file")MultipartFile file,
            @RequestParam("uploadUser") String uploadUser,
            @RequestParam(value = "accessibleBy", required = false) String accessibleBy) {
        try {
            // 1. 上传到minio
            String fileName = file.getOriginalFilename();
            String fileUrl = fileStorageService.uploadFile(file, fileName);

            // 2. 保存到数据库
            KnowledgeDocument document = new KnowledgeDocument();
            document.setDocTitle(fileName);
            document.setUploadUser(uploadUser);
            document.setDocUrl(fileUrl);
            document.setStatus(DocumentStatus.UPLOADED);
            document.setAccessibleBy(accessibleBy);

            documentService.save(document);
            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/{docId}")
    public KnowledgeDocument get(@Positive @PathVariable Long docId) {
        return documentService.getDocument(docId);
    }

    @GetMapping
    public IPage<KnowledgeDocument> page(@Valid @ModelAttribute KnowledgeDocumentQuery query) {
        return documentService.pageDocuments(query);
    }

    @PutMapping("/{docId}")
    public KnowledgeDocument update(@Positive @PathVariable Long docId,
                                    @Valid @RequestBody KnowledgeDocumentRequest request) {
        return documentService.updateDocument(docId, request);
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@Positive @PathVariable Long docId) {
        documentService.deleteDocument(docId);
        return ResponseEntity.noContent().build();
    }
}
