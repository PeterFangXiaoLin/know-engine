package com.knowengine.backend.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowengine.backend.document.dto.KnowledgeSegmentQuery;
import com.knowengine.backend.document.dto.KnowledgeSegmentRequest;
import com.knowengine.backend.document.entity.KnowledgeSegment;
import com.knowengine.backend.document.service.KnowledgeSegmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 知识片段管理接口，支持按所属文档分页查看和维护片段。
 *
 * <p>新增返回 201，查询和更新返回 200，删除返回 204。
 */
@RestController
@RequestMapping("/api/knowledge/segments")
@RequiredArgsConstructor
public class KnowledgeSegmentController {

    private final KnowledgeSegmentService segmentService;

    @PostMapping
    public ResponseEntity<KnowledgeSegment> create(@Valid @RequestBody KnowledgeSegmentRequest request) {
        KnowledgeSegment segment = segmentService.createSegment(request);
        return ResponseEntity.created(URI.create("/api/knowledge/segments/" + segment.getId())).body(segment);
    }

    @GetMapping("/{id}")
    public KnowledgeSegment get(@Positive @PathVariable Long id) {
        return segmentService.getSegment(id);
    }

    @GetMapping
    public IPage<KnowledgeSegment> page(@Valid @ModelAttribute KnowledgeSegmentQuery query) {
        return segmentService.pageSegments(query);
    }

    @PutMapping("/{id}")
    public KnowledgeSegment update(@Positive @PathVariable Long id,
                                   @Valid @RequestBody KnowledgeSegmentRequest request) {
        return segmentService.updateSegment(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Positive @PathVariable Long id) {
        segmentService.deleteSegment(id);
        return ResponseEntity.noContent().build();
    }
}
