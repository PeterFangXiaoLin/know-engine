package com.knowengine.backend.document.dto;

/**
 * 文档切分 dto
 */
public record DocumentSplitParam(String splitType,
                                 Integer chunkSize,
                                 Integer overlap,
                                 Integer titleLevel,
                                 String separator,
                                 String regex) {
}
