package com.knowengine.backend.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档上传和处理所需的基础组件。
 */
@Configuration
public class DocumentConfiguration {

    /**
     * 提供文件内容类型检测器，识别没有 PDF 后缀的 PDF 文件。
     *
     * @return Tika 文件类型检测器
     */
    @Bean
    public Tika tika() {
        return new Tika();
    }
}
