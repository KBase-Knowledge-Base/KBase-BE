package com.kbase.tag.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.security.service.CurrentUserService;
import com.kbase.tag.dto.request.CreateTagRequest;
import com.kbase.tag.dto.request.UpdateTagRequest;
import com.kbase.tag.dto.response.TagResponse;
import com.kbase.tag.service.TagService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST boundary for project-scoped tags. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tags")
public class TagController {

    private final TagService tagService;
    private final CurrentUserService currentUserService;

    public TagController(TagService tagService, CurrentUserService currentUserService) {
        this.tagService = tagService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> listTags(
            @PathVariable UUID projectId,
            @RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(tagService.listTags(
                projectId, query, currentUserService.requirePrincipal()));
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @PatchMapping("/{tagId}")
    public ResponseEntity<TagResponse> renameTag(
            @PathVariable UUID projectId,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest request) {
        return ResponseEntity.ok(tagService.renameTag(
                projectId, tagId, request, currentUserService.requirePrincipal()));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable UUID projectId,
            @PathVariable UUID tagId) {
        tagService.deleteTag(projectId, tagId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
