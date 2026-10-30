package com.knowengine.backend.document.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * @param file
 * @param title
 * @param accessibleBy
 * @param description
 * @param knowledgeBaseType
 */
public record DocumentUploadParam(MultipartFile file,
                                  String title,
                                  String accessibleBy,
                                  String description,
                                  String knowledgeBaseType) {
}
