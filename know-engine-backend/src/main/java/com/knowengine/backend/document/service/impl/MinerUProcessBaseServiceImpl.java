package com.knowengine.backend.document.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.knowengine.backend.document.api.mineru.MineruClient;
import com.knowengine.backend.document.constant.DocumentStatus;
import com.knowengine.backend.document.entity.KnowledgeDocument;
import com.knowengine.backend.document.service.FileProcessService;
import com.knowengine.backend.document.service.FileStorageService;
import com.knowengine.backend.document.service.KnowledgeDocumentService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * minerU 处理服务
 */
@Slf4j
public abstract class MinerUProcessBaseServiceImpl implements FileProcessService {

    private static final long MAX_PDF_BYTES = 200_000_000L;
    private static final long MAX_UNZIP_BYTES = 1_000_000_000L;
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\((<[^>]+>|[^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("(<img\\b[^>]*\\bsrc\\s*=\\s*['\"])([^'\"]+)(['\"])", Pattern.CASE_INSENSITIVE);

    @Autowired
    private MineruClient mineruClient;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private KnowledgeDocumentService documentService;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    /**
     * 调用 MinerU 解析文档，将处理后的 Markdown 和图片存入 MinIO，成功后更新为 CONVERTED。
     *
     * @param document 已保存且处于 UPLOADED 状态的文档
     * @param pdfInput 待转换文件的输入流，由调用方关闭
     * @return 处理后的 Markdown 上传到 MinIO 后的访问URL
     * @throws IOException 解析、文件处理或结果保存失败
     */
    @Override
    public String processDocument(KnowledgeDocument document, InputStream pdfInput) throws IOException {
        if (document == null || document.getDocId() == null) {
            throw new IllegalArgumentException("文档必须先保存到数据库");
        }

        // 1. 原子认领已上传文档，避免同一文档重复进入转换流程。
        JSONObject extension = readExtension(document.getExtension());
        JSONObject progress = new JSONObject();
        progress.put("state", "submitting");
        progress.put("startedAt", Instant.now().toString());
        extension.put("mineru", progress);
        String extensionJson = extension.toJSONString();
        boolean claimed = documentService.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getDocId, document.getDocId())
                .eq(KnowledgeDocument::getStatus, DocumentStatus.UPLOADED)
                .set(KnowledgeDocument::getStatus, DocumentStatus.CONVERTING)
                .set(KnowledgeDocument::getConvertedDocUrl, null)
                .set(KnowledgeDocument::getExtension, extensionJson));
        if (!claimed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文档不存在、正在转换或已完成转换");
        }
        document.setStatus(DocumentStatus.CONVERTING);
        document.setConvertedDocUrl(null);
        document.setExtension(extensionJson);

        Path workDirectory = null;
        List<String> uploadedObjects = new ArrayList<>();
        try {
            // 2. 将上传流落到受限大小的临时文件，再通过签名链接上传到 MinerU。
            workDirectory = Files.createTempDirectory("know-engine-doc-" + document.getDocId() + "-");
            Path pdfFile = workDirectory.resolve("document.pdf");
            copyPdf(pdfInput, pdfFile);
            String batchId = mineruClient.submitPdf(pdfFile, document.getDocId().toString());
            progress.put("batchId", batchId);
            progress.put("state", "processing");
            saveProgress(document, extension, DocumentStatus.CONVERTING, null);

            // 3. 等待解析完成，将结果 ZIP 下载到本地并安全解压。
            MineruClient.TaskResult result = mineruClient.waitForResult(batchId);
            Path archive = workDirectory.resolve("result.zip");
            mineruClient.downloadArchive(result.fullZipUrl(), archive);
            List<Path> files = extractArchive(archive, workDirectory.resolve("extracted"));
            Path markdownFile = findMarkdown(files);

            // 4. 先上传图片并改写引用，再保存 Markdown，保证其图片地址已经可用。
            String prefix = "documents/" + document.getDocId() + "/converted/" + UUID.randomUUID() + "/";
            Map<Path, ImageAsset> images = uploadImages(files, prefix, uploadedObjects);
            if (Files.size(markdownFile) > 50_000_000L) {
                throw new IOException("解析后的 Markdown 超过 50MB 限制");
            }
            String markdown = rewriteImages(Files.readString(markdownFile, StandardCharsets.UTF_8), markdownFile.getParent(), images);
            String markdownObject = prefix + "full.md";
            uploadedObjects.add(markdownObject);
            String markdownUrl = uploadMarkdown(markdownObject, markdown);

            // 5. 所有产物保存成功后才标记完成，避免完成状态下却没有可用 Markdown。
            progress.put("state", "done");
            progress.put("finishedAt", Instant.now().toString());
            saveProgress(document, extension, DocumentStatus.CONVERTED, markdownUrl);
            return markdownUrl;
        } catch (Exception e) {
            // 6. 失败时保留原始 PDF 和诊断信息，恢复为 UPLOADED，清理本次未完成的产物。
            IOException failure = e instanceof IOException io ? io : new IOException("PDF 转换处理失败", e);
            progress.put("state", "failed");
            progress.put("error", failure.getMessage());
            progress.put("finishedAt", Instant.now().toString());
            try {
                saveProgress(document, extension, DocumentStatus.UPLOADED, null);
            } catch (Exception stateError) {
                failure.addSuppressed(stateError);
            }
            cleanupObjects(uploadedObjects, document.getDocId());
            throw failure;
        } finally {
            cleanupDirectory(workDirectory, document.getDocId());
        }
    }

    private void saveProgress(KnowledgeDocument document, JSONObject extension, DocumentStatus status, String convertedUrl) throws IOException {
        String json = extension.toJSONString();
        var update = Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getDocId, document.getDocId())
                .eq(KnowledgeDocument::getStatus, DocumentStatus.CONVERTING)
                .set(KnowledgeDocument::getStatus, status)
                .set(KnowledgeDocument::getExtension, json);
        if (convertedUrl != null) {
            update.set(KnowledgeDocument::getConvertedDocUrl, convertedUrl);
        }
        if (!documentService.update(update)) {
            throw new IOException("文档已删除或处理状态已变化，停止保存转换结果");
        }
        document.setStatus(status);
        document.setExtension(json);
        if (convertedUrl != null) {
            document.setConvertedDocUrl(convertedUrl);
        }
    }

    private JSONObject readExtension(String original) {
        if (original == null || original.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject result = JSON.parseObject(original);
            if (result != null) {
                return result;
            }
        } catch (RuntimeException ignored) {
            // 历史非 JSON 内容保留到新对象中，避免覆盖原有扩展信息。
        }
        JSONObject result = new JSONObject();
        result.put("originalExtension", original);
        return result;
    }

    private void copyPdf(InputStream input, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_PDF_BYTES) {
                    throw new IOException("PDF 不能超过 200MB");
                }
                output.write(buffer, 0, count);
            }
            if (total == 0) {
                throw new IOException("PDF 文件不能为空");
            }
        }
    }

    /**
     * 解压结果包，限制展开体积并校验路径，防止覆盖临时目录以外的文件。
     */
    private List<Path> extractArchive(Path archive, Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<Path> files = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            long total = 0;
            int entries = 0;
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                // 1. 校验条目路径和数量，拒绝 Zip Slip、Windows 盘符及重复文件。
                String name = entry.getName().replace('\\', '/');
                Path target = root.resolve(name).normalize();
                if (++entries > 10_000 || name.contains(":") || !target.startsWith(root) || target.equals(root)) {
                    throw new IOException("MinerU 结果包包含不安全路径或过多文件");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (Files.exists(target)) {
                    throw new IOException("MinerU 结果包包含重复文件");
                }

                // 2. 流式解压并累计展开体积，避免压缩包耗尽磁盘空间。
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_UNZIP_BYTES) {
                            throw new IOException("MinerU 结果包展开后超过 1GB 限制");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                files.add(target);
            }
        }
        return files;
    }

    private Path findMarkdown(List<Path> files) throws IOException {
        List<Path> candidates = files.stream().filter(path -> "full.md".equalsIgnoreCase(path.getFileName().toString())).toList();
        if (candidates.size() != 1) {
            throw new IOException("MinerU 结果包必须包含唯一的 full.md");
        }
        return candidates.getFirst();
    }

    private Map<Path, ImageAsset> uploadImages(List<Path> files, String prefix, List<String> uploaded) throws IOException {
        Map<Path, ImageAsset> images = new HashMap<>();
        for (Path file : files) {
            String type = imageContentType(file);
            if (type == null) {
                continue;
            }
            String objectName = prefix + "images/" + UUID.randomUUID() + extensionOf(file);
            uploaded.add(objectName);
            try {
                String url = fileStorageService.uploadFile(objectName, file, type);
                images.put(file, new ImageAsset(url, describeImage(url)));
            } catch (Exception e) {
                throw new IOException("保存解析图片到 MinIO 失败", e);
            }
        }
        return images;
    }

    private String rewriteImages(String markdown, Path baseDirectory, Map<Path, ImageAsset> images) throws IOException {
        // 1. 改写 Markdown 图片，并按 README 约定填入明确标记的 MOCK 描述。
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        StringBuilder rewritten = new StringBuilder();
        while (matcher.find()) {
            ImageAsset image = resolveImage(matcher.group(2), baseDirectory, images);
            String replacement = image == null ? matcher.group() : "![" + image.description() + "](" + image.url() + ")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);

        // 2. 同时改写表格等 HTML 片段中的 img src，保留其余标签属性。
        matcher = HTML_IMAGE.matcher(rewritten.toString());
        StringBuilder htmlRewritten = new StringBuilder();
        while (matcher.find()) {
            ImageAsset image = resolveImage(matcher.group(2), baseDirectory, images);
            String replacement = image == null ? matcher.group() : matcher.group(1) + image.url() + matcher.group(3);
            matcher.appendReplacement(htmlRewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(htmlRewritten);
        return htmlRewritten.toString();
    }

    private ImageAsset resolveImage(String reference, Path baseDirectory, Map<Path, ImageAsset> images) throws IOException {
        String path = reference.startsWith("<") && reference.endsWith(">") ? reference.substring(1, reference.length() - 1) : reference;
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || path.startsWith("//")) {
            return null;
        }
        path = URLDecoder.decode(path.split("[?#]", 2)[0].replace("+", "%2B"), StandardCharsets.UTF_8);
        ImageAsset image = images.get(baseDirectory.resolve(path.replace('\\', '/')).normalize());
        if (image == null) {
            throw new IOException("Markdown 引用的本地图片不在解析结果中");
        }
        return image;
    }

    /**
     * 生成图片描述
     *
     * @param imageUrl 图片地址
     * @return 图片描述
     */
    private String describeImage(String imageUrl) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(chatModelApiKey)
                .baseUrl(chatModelBaseUrl)
                .modelName("qwen3.8-omni-flash")
                .temperature(0.7)
                .logResponses(true)
                .logRequests(true)
                .build();

        UserMessage userMessage = UserMessage.from(new TextContent("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明，不要增加任何特殊符号，特别是换行符"), new ImageContent(imageUrl));
        return chatModel.chat(userMessage).aiMessage().text();
    }

    private String uploadMarkdown(String objectName, String markdown) throws IOException {
        try {
            return fileStorageService.uploadFile(objectName, markdown.getBytes(StandardCharsets.UTF_8), "text/markdown; charset=utf-8");
        } catch (Exception e) {
            throw new IOException("保存解析 Markdown 到 MinIO 失败", e);
        }
    }

    private String imageContentType(Path path) {
        return switch (extensionOf(path)) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".svg" -> "image/svg+xml";
            default -> null;
        };
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private void cleanupObjects(List<String> objects, Long docId) {
        for (String object : objects) {
            try {
                fileStorageService.deleteFile(object);
            } catch (Exception ignored) {
                log.warn("清理转换产物失败，documentId={}，object={}", docId, object);
            }
        }
    }

    private void cleanupDirectory(Path directory, Long docId) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("清理文档转换临时目录失败，documentId={}", docId);
        }
    }

    private record ImageAsset(String url, String description) {
    }
}
