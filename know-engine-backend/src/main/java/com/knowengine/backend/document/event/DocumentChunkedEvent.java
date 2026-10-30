package com.knowengine.backend.document.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * 文档已分段事件
 * 当文档状态变更为CHUNKED时发送此事件
 */
@Getter
@ToString
public class DocumentChunkedEvent extends ApplicationEvent {

    /**
     * 文档id
     */
    private final Long documentId;

    /**
     * 文档的版本号
     */
    private final Long documentVersionId;

    /**
     * 分段数量
     */
    private final int segmentCount;

    public DocumentChunkedEvent(Object source, Long documentId, Long documentVersionId, int segmentCount) {
        super(source);
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.segmentCount = segmentCount;
    }
}
