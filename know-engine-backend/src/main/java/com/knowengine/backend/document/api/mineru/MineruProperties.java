package com.knowengine.backend.document.api.mineru;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MinerU 精准解析 API 配置，Token 从环境变量或外部本地配置读取。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mineru")
public class MineruProperties {

    /**
     * MinerU 服务地址，不包含具体接口路径。
     */
    private String baseUrl = "https://mineru.net";

    /**
     * API Token，仅用于 MinerU 管理接口的 Bearer 鉴权。
     */
    private String token;

    /**
     * 解析模型版本，默认使用官方推荐的 vlm。
     */
    private String modelVersion = "vlm";

    /**
     * 文档语言，ch 表示中文。
     */
    private String language = "ch";

    /**
     * 单次 HTTP 请求超时，包含连接、上传和读取响应。
     */
    private Duration httpTimeout = Duration.ofMinutes(2);

    /**
     * 解析任务的轮询间隔。
     */
    private Duration pollInterval = Duration.ofSeconds(3);

    /**
     * 等待解析结果的总超时。
     */
    private Duration pollTimeout = Duration.ofMinutes(10);
}
