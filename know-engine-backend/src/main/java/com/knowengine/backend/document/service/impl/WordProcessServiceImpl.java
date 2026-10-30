package com.knowengine.backend.document.service.impl;

import com.knowengine.backend.document.constant.FileType;
import com.knowengine.backend.document.constant.KnowledgeBaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * word文档处理服务实现类
 */
@Slf4j
@Service
public class WordProcessServiceImpl extends MinerUProcessBaseServiceImpl {

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.DOC;
    }
}
