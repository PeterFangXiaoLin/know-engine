package com.knowengine.backend.document.api.mineru;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MineruClientTests {

    private Path directory;

    private HttpServer server;
    private String baseUrl;
    private MineruProperties properties;
    private volatile String ticketResponse;
    private volatile IntFunction<String> resultResponse;
    private final AtomicInteger queries = new AtomicInteger();
    private final AtomicInteger uploads = new AtomicInteger();
    private final AtomicReference<String> apiAuthorization = new AtomicReference<>();
    private final AtomicReference<String> uploadAuthorization = new AtomicReference<>();
    private final AtomicReference<String> downloadAuthorization = new AtomicReference<>();
    private final AtomicReference<String> uploadContentType = new AtomicReference<>();
    private final AtomicReference<byte[]> uploadedPdf = new AtomicReference<>();
    private final AtomicReference<String> submittedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        // 将测试临时目录放在项目内，兼容受限 Windows 环境的路径解析和清理权限。
        directory = Files.createDirectories(Path.of("target", "mineru-client-tests", UUID.randomUUID().toString())).toAbsolutePath();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ticketResponse = "{\"code\":0,\"data\":{\"batch_id\":\"batch-1\",\"file_urls\":[\"" + baseUrl + "/upload\"]}}";
        resultResponse = count -> result("done", "");
        server.createContext("/", this::handleRequest);
        server.start();

        properties = new MineruProperties();
        properties.setBaseUrl(baseUrl);
        properties.setToken("test-mineru-token");
        properties.setHttpTimeout(Duration.ofSeconds(2));
        properties.setPollInterval(Duration.ofMillis(5));
        properties.setPollTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.stop(0);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void uploadsRawPdfPollsUntilDoneAndDoesNotSendTokenToStorageLinks() throws Exception {
        // 1. 模拟排队、运行和完成状态，上传本地 PDF。
        resultResponse = count -> result(count == 1 ? "pending" : count == 2 ? "running" : "done", "");
        Path pdf = Files.writeString(directory.resolve("input.pdf"), "%PDF-1.4\nlocal test");
        MineruClient client = new MineruClient(properties);
        String batchId = client.submitPdf(pdf, "42");
        MineruClient.TaskResult result = client.waitForResult(batchId);

        // 2. 下载结果，并核对三类 HTTP 请求的鉴权和报文格式。
        Path archive = directory.resolve("output.zip");
        client.downloadArchive(result.fullZipUrl(), archive);
        assertThat(apiAuthorization.get()).isEqualTo("Bearer test-mineru-token");
        assertThat(uploadAuthorization.get()).isNull();
        assertThat(downloadAuthorization.get()).isNull();
        assertThat(uploadContentType.get()).isNull();
        assertThat(uploadedPdf.get()).isEqualTo(Files.readAllBytes(pdf));
        assertThat(JSON.parseObject(submittedBody.get()).getJSONArray("files").getJSONObject(0).getString("data_id")).isEqualTo("42");
        assertThat(queries.get()).isEqualTo(3);
        assertThat(Files.readString(archive)).isEqualTo("test-result-zip");
    }

    @Test
    void nonzeroApiCodeStopsUploadAndRedactsTokenFromError() throws Exception {
        ticketResponse = "{\"code\":\"A0202\",\"msg\":\"invalid test-mineru-token\"}";
        Path pdf = Files.writeString(directory.resolve("input.pdf"), "%PDF-1.4");

        assertThatThrownBy(() -> new MineruClient(properties).submitPdf(pdf, "42"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("A0202")
                .hasMessageNotContaining("test-mineru-token");
        assertThat(uploads.get()).isZero();
    }

    @Test
    void failedTaskStopsPolling() {
        resultResponse = count -> result("failed", "unsupported pdf");

        assertThatThrownBy(() -> new MineruClient(properties).waitForResult("batch-1"))
                .isInstanceOf(IOException.class).hasMessageContaining("unsupported pdf");
        assertThat(queries.get()).isEqualTo(1);
    }

    @Test
    void pollingHasAnOverallDeadline() {
        resultResponse = count -> result("pending", "");
        properties.setPollTimeout(Duration.ofMillis(50));
        long started = System.nanoTime();

        assertThatThrownBy(() -> new MineruClient(properties).waitForResult("batch-1"))
                .isInstanceOf(IOException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void interruptionIsPreservedWithoutMakingAnHttpRequest() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new MineruClient(properties).waitForResult("batch-1"))
                    .isInstanceOf(InterruptedIOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(queries.get()).isZero();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void missingTokenAndMalformedResponsesFailClearly() {
        resultResponse = count -> "{\"code\":0,\"data\":{}}";
        assertThatThrownBy(() -> new MineruClient(properties).getTaskResult("batch-1"))
                .isInstanceOf(IOException.class).hasMessageContaining("extract_result");

        properties.setToken("");
        assertThatThrownBy(() -> new MineruClient(properties).getTaskResult("batch-1"))
                .isInstanceOf(IOException.class).hasMessageContaining("Token");
    }

    private String result(String state, String error) {
        return "{\"code\":0,\"data\":{\"extract_result\":[{\"state\":\"" + state
                + "\",\"err_msg\":\"" + error + "\",\"full_zip_url\":\"" + baseUrl + "/archive.zip\"}]}}";
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String response;
            if (path.equals("/api/v4/file-urls/batch")) {
                apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                submittedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                response = ticketResponse;
            } else if (path.equals("/upload")) {
                uploadAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                uploadedPdf.set(exchange.getRequestBody().readAllBytes());
                uploads.incrementAndGet();
                response = "uploaded";
            } else if (path.equals("/api/v4/extract-results/batch/batch-1")) {
                apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                response = resultResponse.apply(queries.incrementAndGet());
            } else if (path.equals("/archive.zip")) {
                downloadAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                response = "test-result-zip";
            } else {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
