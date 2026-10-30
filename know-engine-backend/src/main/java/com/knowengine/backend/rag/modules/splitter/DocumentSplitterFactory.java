package com.knowengine.backend.rag.modules.splitter;

import com.knowengine.backend.document.constant.SplitType;
import com.knowengine.backend.document.dto.DocumentSplitParam;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;

/**
 * 获取对应的切分工厂
 */
public class DocumentSplitterFactory {
    public static DocumentSplitter getInstance(DocumentSplitParam documentSplitParam) {
        // 按标题切分
        if (SplitType.TITLE.name().equals(documentSplitParam.splitType())) {
            // 处理几级标题
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.titleLevel(), false, false, documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按长度
        if (SplitType.LENGTH.name().equals(documentSplitParam.splitType())) {
            return new DocumentByWordSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按分隔符
        if (SplitType.SEPARATOR.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.separator(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按正则
        if (SplitType.REGEX.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.regex(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 智能切分
        if (SplitType.SMART.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), (int) (documentSplitParam.chunkSize() * 0.1));
        }

        return null;
    }
}
