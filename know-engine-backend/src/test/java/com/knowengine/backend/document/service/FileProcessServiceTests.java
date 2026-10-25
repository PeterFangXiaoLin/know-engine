package com.knowengine.backend.document.service;

import com.alibaba.fastjson2.JSON;
import com.knowengine.backend.document.api.mineru.MineruClient;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.dto.KnowledgeDocumentRequest;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:file_process;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "minio.endpoint=http://localhost:19000", "minio.access-key=test-key", "minio.secret-key=test-secret",
        "minio.bucket-name=test-bucket",
        "elasticsearch.host=http://localhost:19200", "elasticsearch.username=test", "elasticsearch.password=test",
        "elasticsearch.base-url=http://localhost:18080/v1", "elasticsearch.model-name=test-embedding",
        "elasticsearch.api-key=test-embedding-key", "elasticsearch.dimensions=1536",
        "mineru.token=test-mineru-token"
})
@AutoConfigureMockMvc
class FileProcessServiceTests {

    @Autowired
    private FileProcessService processService;

    @Autowired
    private KnowledgeDocumentService documentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MineruClient mineruClient;

    @MockitoBean
    private FileStorageService storage;

    private final AtomicReference<Path> workDirectory = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_document (
                    doc_id BIGINT AUTO_INCREMENT PRIMARY KEY, doc_title VARCHAR(1024) NOT NULL,
                    upload_user VARCHAR(255), doc_url VARCHAR(2048), converted_doc_url VARCHAR(2048),
                    status VARCHAR(32) NOT NULL, accessible_by VARCHAR(1024), extension TEXT)
                """);
        jdbc.update("DELETE FROM knowledge_document");
        when(storage.uploadFile(any(org.springframework.web.multipart.MultipartFile.class), anyString()))
                .thenReturn("https://storage.example/original.pdf");
        when(storage.uploadFile(anyString(), any(Path.class), anyString()))
                .thenReturn("https://storage.example/figure.png");
        when(storage.uploadFile(anyString(), any(byte[].class), anyString()))
                .thenReturn("https://storage.example/full.md");
        when(mineruClient.submitPdf(any(Path.class), anyString())).thenAnswer(invocation -> {
            workDirectory.set(invocation.<Path>getArgument(0).getParent());
            assertThat(documentService.getDocument(Long.valueOf(invocation.<String>getArgument(1))).getStatus())
                    .isEqualTo(DocumentStatus.CONVERTING);
            return "batch-1";
        });
        when(mineruClient.waitForResult("batch-1"))
                .thenReturn(new MineruClient.TaskResult("batch-1", "done", "https://mineru.example/result.zip", null));
        archive(Map.of("document/full.md", "# 标题\n![](images/figure.png)\n<img src=\"./images/figure.png\" />\n![远程](https://example.com/remote.png)",
                "document/images/figure.png", "image-bytes"));
    }

    @Test
    void pdfUploadPersistsConvertedMarkdownAndRewritesImages() throws Exception {
        // 1. 从实际上传 Controller 进入转换流程。
        MockMultipartFile file = new MockMultipartFile("file", "演示.PDF", "application/pdf", "%PDF-1.4 demo".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/knowledge/documents/upload").file(file).param("uploadUser", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.convertedDocUrl").value("https://storage.example/full.md"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long docId = JSON.parseObject(response).getLongValue("docId");

        // 2. 验证 Markdown 图片和 HTML 图片地址均已改写，远程引用保持可用。
        ArgumentCaptor<byte[]> markdown = ArgumentCaptor.forClass(byte[].class);
        verify(storage).uploadFile(anyString(), markdown.capture(), eq("text/markdown; charset=utf-8"));
        String text = new String(markdown.getValue(), StandardCharsets.UTF_8);
        assertThat(text).contains("MOCK", "](https://storage.example/figure.png)", "src=\"https://storage.example/figure.png\"",
                "https://example.com/remote.png");

        // 3. 验证实际数据库状态、任务诊断信息和临时文件清理。
        KnowledgeDocument stored = documentService.getDocument(docId);
        assertThat(stored.getStatus()).isEqualTo(DocumentStatus.CONVERTED);
        assertThat(JSON.parseObject(stored.getExtension()).getJSONObject("mineru").getString("batchId")).isEqualTo("batch-1");
        assertThat(Files.exists(workDirectory.get())).isFalse();
    }

    @Test
    void mineruFailurePreservesOriginalDocumentAndRecordsError() throws Exception {
        KnowledgeDocument document = uploadedDocument();
        doThrow(new IOException("MinerU 接口失败，code=A0202")).when(mineruClient).waitForResult("batch-1");

        assertThatThrownBy(() -> processService.processDocument(document, pdfInput())).isInstanceOf(IOException.class);

        KnowledgeDocument stored = documentService.getDocument(document.getDocId());
        assertThat(stored.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(stored.getDocUrl()).isEqualTo("https://storage.example/original.pdf");
        assertThat(stored.getConvertedDocUrl()).isNull();
        assertThat(JSON.parseObject(stored.getExtension()).getString("source")).isEqualTo("test");
        assertThat(JSON.parseObject(stored.getExtension()).getJSONObject("mineru").getString("error")).contains("A0202");
        assertThat(Files.exists(workDirectory.get())).isFalse();
    }

    @Test
    void rejectsArchivePathsOutsideExtractionDirectory() throws Exception {
        KnowledgeDocument document = uploadedDocument();
        String escapedName = "escaped-" + java.util.UUID.randomUUID() + ".txt";
        archive(Map.of("../../" + escapedName, "unsafe", "full.md", "text"));

        assertThatThrownBy(() -> processService.processDocument(document, pdfInput()))
                .isInstanceOf(IOException.class).hasMessageContaining("不安全路径");
        assertThat(Files.exists(workDirectory.get().getParent().resolve(escapedName))).isFalse();
        assertThat(documentService.getDocument(document.getDocId()).getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void missingMarkdownIsNotMarkedConverted() throws Exception {
        KnowledgeDocument document = uploadedDocument();
        archive(Map.of("content.json", "{}"));

        assertThatThrownBy(() -> processService.processDocument(document, pdfInput()))
                .isInstanceOf(IOException.class).hasMessageContaining("full.md");
        assertThat(documentService.getDocument(document.getDocId()).getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void storageFailureCleansOnlyThisAttemptsArtifacts() throws Exception {
        KnowledgeDocument document = uploadedDocument();
        when(storage.uploadFile(anyString(), any(byte[].class), anyString())).thenThrow(new IOException("storage failed"));

        assertThatThrownBy(() -> processService.processDocument(document, pdfInput()))
                .isInstanceOf(IOException.class).hasMessageContaining("MinIO");
        ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).deleteFile(removed.capture());
        assertThat(removed.getAllValues()).allMatch(name -> name.startsWith("documents/" + document.getDocId() + "/converted/"));
        assertThat(documentService.getDocument(document.getDocId()).getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void nonPdfUploadSkipsMineruAndEmptyFileIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "plain text".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/knowledge/documents/upload").file(file).param("uploadUser", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.convertedDocUrl").value("https://storage.example/original.pdf"));
        mockMvc.perform(multipart("/knowledge/documents/upload")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])).param("uploadUser", "tester"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mineruClient);
    }

    @Test
    void alreadyConvertedDocumentCannotBeClaimedAgain() {
        KnowledgeDocument document = uploadedDocument();
        document.setStatus(DocumentStatus.CONVERTED);
        documentService.updateById(document);

        assertThatThrownBy(() -> processService.processDocument(document, pdfInput()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(mineruClient);
    }

    private KnowledgeDocument uploadedDocument() {
        KnowledgeDocumentRequest request = new KnowledgeDocumentRequest();
        request.setDocTitle("document.pdf");
        request.setStatus(DocumentStatus.UPLOADED);
        request.setDocUrl("https://storage.example/original.pdf");
        request.setExtension("{\"source\":\"test\"}");
        return documentService.createDocument(request);
    }

    private ByteArrayInputStream pdfInput() {
        return new ByteArrayInputStream("%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
    }

    private void archive(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        doAnswer(invocation -> {
            Files.write(invocation.getArgument(1), bytes.toByteArray());
            return null;
        }).when(mineruClient).downloadArchive(anyString(), any(Path.class));
    }
}
