package com.knowengine.backend.document.service;

import com.knowengine.backend.document.constant.FileType;
import com.knowengine.backend.document.constant.KnowledgeBaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工厂模式
 */
@Service
public class FileProcessServiceFactory {

    @Autowired
    List<FileProcessService> fileProcessServiceList;

    public FileProcessService get(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileProcessServiceList.stream()
                .filter(service -> service.supports(fileType, knowledgeBaseType))
                .findFirst()
                .orElse(null);
    }
}
