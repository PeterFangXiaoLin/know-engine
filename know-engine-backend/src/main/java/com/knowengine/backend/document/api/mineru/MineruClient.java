package com.knowengine.backend.document.api.mineru;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 使用 OkHttp 调用 MinerU v4：申请上传链接、上传 PDF、查询任务、下载结果 ZIP。
 * 签名上传链接和结果下载链接不携带 API Token。
 */
@Component
public class MineruClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_PDF_BYTES = 200_000_000L;
    private static final long MAX_ARCHIVE_BYTES = 512_000_000L;
    private static final Set<String> PENDING_STATES = Set.of("waiting-file", "pending", "running", "converting", "uploading");

    private final MineruProperties properties;
    private final OkHttpClient httpClient;

    /**
     * 创建可复用的 HTTP 客户端，关闭自动重试，避免重复提交解析任务。
     *
     * @param properties MinerU 配置
     */
    public MineruClient(MineruProperties properties) {
        this.properties = properties;
        requirePositive(properties.getHttpTimeout(), "http-timeout");
        requirePositive(properties.getPollInterval(), "poll-interval");
        requirePositive(properties.getPollTimeout(), "poll-timeout");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .callTimeout(properties.getHttpTimeout())
                .readTimeout(properties.getHttpTimeout())
                .writeTimeout(properties.getHttpTimeout())
                .retryOnConnectionFailure(false)
                .build();
    }

    /**
     * 通过签名链接上传一个 PDF，上传完成后由 MinerU 自动创建解析任务。
     *
     * @param pdfFile 本地 PDF 临时文件
     * @param dataId 业务关联ID，例如文档ID
     * @return 用于查询结果的 batch_id
     * @throws IOException 上传或接口调用失败
     */
    public String submitPdf(Path pdfFile, String dataId) throws IOException {
        long size = Files.size(pdfFile);
        if (size <= 0 || size > MAX_PDF_BYTES) {
            throw new IOException("PDF 必须非空且不能超过 200MB");
        }

        // 1. 申请单文件签名上传链接，使用 data_id 关联本地文档。
        String json = JSON.toJSONString(Map.of(
                "files", List.of(Map.of("name", "document.pdf", "data_id", dataId)),
                "model_version", properties.getModelVersion(),
                "language", properties.getLanguage(),
                "enable_formula", true,
                "enable_table", true));
        JSONObject data = executeApi(apiRequest("api/v4/file-urls/batch", null)
                .post(RequestBody.create(json, JSON_TYPE)).build(), 0);
        String batchId = requiredString(data, "batch_id");
        JSONArray urls = data.getJSONArray("file_urls");
        if (urls == null || urls.size() != 1) {
            throw new IOException("MinerU 未返回单文件上传链接");
        }

        // 2. 直接 PUT 文件字节，不使用 multipart，也不向对象存储发送 API Token。
        Request upload = new Request.Builder().url(urls.getString(0))
                .put(RequestBody.create(pdfFile.toFile(), null)).build();
        try (Response response = httpClient.newCall(upload).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("MinerU 文件上传失败，HTTP " + response.code());
            }
        }
        return batchId;
    }

    /**
     * 查询单文件批次的当前解析状态。
     *
     * @param batchId 提交文件后返回的批次ID
     * @return 当前状态、结果 ZIP 链接和失败原因
     * @throws IOException 查询失败或响应结构不合法
     */
    public TaskResult getTaskResult(String batchId) throws IOException {
        return queryResult(batchId, 0);
    }

    /**
     * 有限时地轮询任务，解析失败、超时或线程中断时终止等待。
     *
     * @param batchId 批次ID
     * @return 已完成且包含结果 ZIP 链接的任务
     * @throws IOException 解析失败或等待中断、超时
     */
    public TaskResult waitForResult(String batchId) throws IOException {
        long deadline = System.nanoTime() + properties.getPollTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("MinerU 解析等待已中断");
            }

            // 1. 单次查询也受总截止时间约束，避免 HTTP 请求超过轮询超时。
            TaskResult result = queryResult(batchId, deadline);
            if ("done".equals(result.state())) {
                if (!StringUtils.hasText(result.fullZipUrl())) {
                    throw new IOException("MinerU 解析完成但未返回 full_zip_url");
                }
                return result;
            }
            if ("failed".equals(result.state())) {
                throw new IOException("MinerU 解析失败：" + safeMessage(result.errorMessage()));
            }
            if (!PENDING_STATES.contains(result.state())) {
                throw new IOException("MinerU 返回未知任务状态：" + safeMessage(result.state()));
            }

            // 2. 只在未完成时等待，并保留线程的中断标记。
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(properties.getPollInterval().toNanos(), remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted = new InterruptedIOException("MinerU 解析等待已中断");
                interrupted.initCause(e);
                throw interrupted;
            }
        }
        throw new IOException("等待 MinerU 解析结果超时，batchId=" + batchId);
    }

    /**
     * 将解析结果 ZIP 流式保存到本地，限制下载体积，避免整包占用内存。
     *
     * @param resultUrl MinerU 返回的 full_zip_url
     * @param destination 本地目标文件
     * @throws IOException 下载失败或结果包超过限制
     */
    public void downloadArchive(String resultUrl, Path destination) throws IOException {
        Request request = new Request.Builder().url(resultUrl).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("MinerU 结果包下载失败，HTTP " + response.code());
            }
            if (body.contentLength() > MAX_ARCHIVE_BYTES) {
                throw new IOException("MinerU 结果包超过 512MB 下载限制");
            }
            try (InputStream input = body.byteStream(); OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ARCHIVE_BYTES) {
                        throw new IOException("MinerU 结果包超过 512MB 下载限制");
                    }
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private TaskResult queryResult(String batchId, long deadline) throws IOException {
        JSONObject data = executeApi(apiRequest("api/v4/extract-results/batch", batchId).get().build(), deadline);
        JSONArray results = data.getJSONArray("extract_result");
        if (results == null) {
            throw new IOException("MinerU 查询响应缺少 extract_result");
        }
        if (results.isEmpty()) {
            return new TaskResult(batchId, "pending", null, null);
        }
        if (results.size() != 1) {
            throw new IOException("MinerU 单文件批次返回了多个结果");
        }
        JSONObject result = results.getJSONObject(0);
        return new TaskResult(batchId, requiredString(result, "state"),
                result.getString("full_zip_url"), result.getString("err_msg"));
    }

    private Request.Builder apiRequest(String path, String id) throws IOException {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new IOException("未配置 MinerU Token，请设置 MINERU_TOKEN 或外部 mineru.token");
        }
        HttpUrl.Builder url = HttpUrl.get(properties.getBaseUrl()).newBuilder().addPathSegments(path);
        if (id != null) {
            url.addPathSegment(id);
        }
        return new Request.Builder().url(url.build())
                .header("Authorization", "Bearer " + properties.getToken().trim())
                .header("Accept", "application/json");
    }

    private JSONObject executeApi(Request request, long deadline) throws IOException {
        Call call = httpClient.newCall(request);
        if (deadline != 0) {
            call.timeout().deadlineNanoTime(deadline);
        }
        try (Response response = call.execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("MinerU 接口调用失败，HTTP " + response.code());
            }
            byte[] bytes = body.byteStream().readNBytes(2_000_001);
            if (bytes.length > 2_000_000) {
                throw new IOException("MinerU JSON 响应超过大小限制");
            }
            JSONObject result;
            try {
                result = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                throw new IOException("MinerU 返回了无效的 JSON 响应");
            }
            if (result == null || !"0".equals(result.getString("code"))) {
                throw new IOException("MinerU 接口失败，code=" + (result == null ? "null" : safeMessage(result.getString("code")))
                        + "，msg=" + (result == null ? "空响应" : safeMessage(result.getString("msg"))));
            }
            JSONObject data = result.getJSONObject("data");
            if (data == null) {
                throw new IOException("MinerU 响应缺少 data");
            }
            return data;
        }
    }

    private String safeMessage(String message) {
        String safe = message == null ? "未知原因" : message;
        if (StringUtils.hasText(properties.getToken())) {
            safe = safe.replace(properties.getToken(), "[redacted]");
        }
        safe = safe.replace('\r', ' ').replace('\n', ' ');
        return safe.substring(0, Math.min(safe.length(), 500));
    }

    private static String requiredString(JSONObject object, String key) throws IOException {
        String value = object.getString(key);
        if (!StringUtils.hasText(value)) {
            throw new IOException("MinerU 响应缺少 " + key);
        }
        return value;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("mineru." + name + " 必须大于 0");
        }
    }

    /**
     * 单文件解析结果。
     *
     * @param batchId 批次ID
     * @param state MinerU 任务状态
     * @param fullZipUrl 解析结果 ZIP 链接，仅 done 时存在
     * @param errorMessage 解析失败原因，仅 failed 时有效
     */
    public record TaskResult(String batchId, String state, String fullZipUrl, String errorMessage) {
    }
}
