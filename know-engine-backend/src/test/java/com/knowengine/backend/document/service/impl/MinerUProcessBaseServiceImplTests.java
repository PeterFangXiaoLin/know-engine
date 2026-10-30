package com.knowengine.backend.document.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.knowengine.backend.document.api.mineru.MineruClient;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.constant.KnowledgeBaseType;
import com.knowengine.backend.document.dto.DocumentUploadParam;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.service.FileProcessService;
import com.knowengine.backend.document.service.FileProcessServiceFactory;
import com.knowengine.backend.document.service.FileStorageService;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinerUProcessBaseServiceImplTests {

    private static final String ORIGINAL_URL = "https://minio.example/original/document.pdf";
    private static final String ZIP_URL = "https://mineru.example/result.zip";
    private static final String MARKDOWN_URL = "https://minio.example/converted/full.md";

    private MineruClient mineruClient;
    private FileStorageService storageService;
    private KnowledgeDocumentService documentService;
    private final AtomicReference<KnowledgeDocument> savedDocument = new AtomicReference<>();

    @BeforeAll
    static void initializeEntityMapping() {
        // 只初始化 Lambda 条件所需的字段映射，测试不连接数据库或外部服务。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), KnowledgeDocument.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        mineruClient = mock(MineruClient.class);
        storageService = mock(FileStorageService.class);
        documentService = mock(KnowledgeDocumentService.class);
        when(documentService.update(any())).thenReturn(true);
        when(documentService.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        when(documentService.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setDocId(42L);
            savedDocument.set(document);
            return true;
        });
        when(mineruClient.submitPdf(any(Path.class), anyString())).thenReturn("batch-42");
        when(mineruClient.waitForResult("batch-42"))
                .thenReturn(new MineruClient.TaskResult("batch-42", "done", ZIP_URL, null));
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(MARKDOWN_URL);

        byte[] archive = markdownArchive();
        doAnswer(invocation -> {
            Files.write(invocation.<Path>getArgument(1), archive);
            return null;
        }).when(mineruClient).downloadArchive(anyString(), any(Path.class));
    }

    @ParameterizedTest
    @MethodSource("processors")
    void returnsUploadedMarkdownUrlForPdfAndWord(FileProcessService processor) throws Exception {
        injectDependencies(processor);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocId(42L);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setDocUrl(ORIGINAL_URL);

        String result;
        try (InputStream input = fileInput()) {
            result = processor.processDocument(document, input);
        }

        // 返回的是处理后 Markdown 的 MinIO 地址，而非原文件地址或 MinerU ZIP 地址。
        assertThat(result).isEqualTo(MARKDOWN_URL).isNotEqualTo(ORIGINAL_URL).isNotEqualTo(ZIP_URL);
        assertThat(document.getConvertedDocUrl()).isEqualTo(result);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.CONVERTED);
    }

    @Test
    void uploadSavesReturnedUrlAndClosesInputStream() throws Exception {
        InputStream input = spy(fileInput());
        DocumentProcessServiceImpl uploadService = uploadService();
        DocumentUploadParam parameters = uploadParameters(input);

        KnowledgeDocument result = uploadService.upload(parameters, "tester");

        assertThat(result.getConvertedDocUrl()).isEqualTo(MARKDOWN_URL);
        assertThat(result.getDocUrl()).isEqualTo(ORIGINAL_URL);
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.CONVERTED);
        verify(documentService).updateById(same(result));
        verify(input).close();
    }

    @Test
    void conversionFailurePropagatesAndClosesInputStream() throws Exception {
        InputStream input = spy(fileInput());
        DocumentProcessServiceImpl uploadService = uploadService();
        DocumentUploadParam parameters = uploadParameters(input);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
                .thenThrow(new IOException("MinIO unavailable"));

        assertThatThrownBy(() -> uploadService.upload(parameters, "tester"))
                .isInstanceOf(IOException.class).hasMessageContaining("Markdown");

        assertThat(savedDocument.get().getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(savedDocument.get().getConvertedDocUrl()).isNull();
        verify(documentService, never()).updateById(any(KnowledgeDocument.class));
        verify(input).close();
    }

    private static Stream<FileProcessService> processors() {
        return Stream.of(new PdfProcessServiceImpl(), new WordProcessServiceImpl());
    }

    private DocumentProcessServiceImpl uploadService() {
        PdfProcessServiceImpl processor = new PdfProcessServiceImpl();
        injectDependencies(processor);

        FileProcessServiceFactory factory = new FileProcessServiceFactory();
        ReflectionTestUtils.setField(factory, "fileProcessServiceList", List.of(processor));

        DocumentProcessServiceImpl service = new DocumentProcessServiceImpl();
        ReflectionTestUtils.setField(service, "fileStorageService", storageService);
        ReflectionTestUtils.setField(service, "knowledgeDocumentService", documentService);
        ReflectionTestUtils.setField(service, "fileProcessServiceFactory", factory);
        return service;
    }

    private void injectDependencies(FileProcessService processor) {
        ReflectionTestUtils.setField(processor, "mineruClient", mineruClient);
        ReflectionTestUtils.setField(processor, "fileStorageService", storageService);
        ReflectionTestUtils.setField(processor, "documentService", documentService);
    }

    private DocumentUploadParam uploadParameters(InputStream input) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("document.pdf");
        when(file.getInputStream()).thenReturn(input);
        when(storageService.uploadFile(same(file), anyString())).thenReturn(ORIGINAL_URL);
        return new DocumentUploadParam(file, "文档", "developers", "测试文档", KnowledgeBaseType.DOCUMENT_SEARCH.name());
    }

    private ByteArrayInputStream fileInput() {
        return new ByteArrayInputStream("test document".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] markdownArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("parsed/full.md"));
            zip.write("# 处理后的 Markdown".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
