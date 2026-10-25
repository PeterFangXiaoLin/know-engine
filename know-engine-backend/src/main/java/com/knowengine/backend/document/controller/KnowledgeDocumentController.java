package com.knowengine.backend.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.dto.KnowledgeDocumentQuery;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.service.FileStorageService;
import com.knowengine.backend.document.service.FileProcessService;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

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

    private final FileStorageService fileStorageService;

    private final FileProcessService fileProcessService;

    private final Tika tika;

    /**
     * 上传文件，PDF 同步解析为 Markdown，其他文件直接标记转换完成。
     *
     * @param file         文件
     * @param uploadUser   上传用户
     * @param accessibleBy 文件可见范围
     * @return 包含原始文件URL、转换后URL和最新状态的文档
     */
    @PostMapping("/upload")
    public KnowledgeDocument uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadUser") String uploadUser,
            @RequestParam(value = "accessibleBy", required = false) String accessibleBy) {
        if (file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空文件");
        }
        KnowledgeDocument document = new KnowledgeDocument();
        try {
            // 1. 检测文件类型并上传原始文件；独立对象前缀避免同名文件相互覆盖。
            String fileName = file.getOriginalFilename().replace('\\', '/');
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            boolean pdf = isPdfFile(fileName) || isPdfContent(file);
            String fileUrl = fileStorageService.uploadFile(file, "documents/original/" + UUID.randomUUID() + "/" + fileName);

            // 2. 保存已上传文档，后续转换失败时仍保留原始文件和文档记录。
            document.setDocTitle(fileName);
            document.setUploadUser(uploadUser);
            document.setDocUrl(fileUrl);
            document.setStatus(DocumentStatus.UPLOADED);
            document.setAccessibleBy(accessibleBy);

            if (!documentService.save(document)) {
                throw new IOException("保存文档记录失败");
            }

            // 3. PDF 由处理服务完成 MinerU 解析、产物存储和状态更新。
            if (pdf) {
                try (InputStream input = file.getInputStream()) {
                    return fileProcessService.processDocument(document, input);
                }
            }

            // 4. 非 PDF 沿用原始地址作为转换结果。
            KnowledgeDocument update = new KnowledgeDocument();
            update.setDocId(document.getDocId());
            update.setStatus(DocumentStatus.CONVERTED);
            update.setConvertedDocUrl(fileUrl);
            if (!documentService.updateById(update)) {
                throw new IOException("保存文档转换状态失败");
            }
            document.setStatus(DocumentStatus.CONVERTED);
            document.setConvertedDocUrl(fileUrl);
            return document;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("文件上传或转换失败，documentId={}，异常类型={}", document.getDocId(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "文件上传或转换失败，documentId=" + document.getDocId() + "，已保存文档的转换原因可查看 extension.mineru", e);
        }
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
     * @param docId 文档ID
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
