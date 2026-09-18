package com.kbase.folder.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.folder.dto.request.CreateFolderRequest;
import com.kbase.folder.dto.request.UpdateFolderRequest;
import com.kbase.folder.dto.response.FolderResponse;
import com.kbase.folder.service.FolderService;
import com.kbase.security.service.CurrentUserService;

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

/** REST boundary for project-scoped folder hierarchy. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/folders")
public class FolderController {

    private final FolderService folderService;
    private final CurrentUserService currentUserService;

    public FolderController(FolderService folderService, CurrentUserService currentUserService) {
        this.folderService = folderService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(
            @PathVariable UUID projectId,
            @RequestParam(name = "parentId", required = false) UUID parentId) {
        return ResponseEntity.ok(folderService.listFolders(
                projectId, parentId, currentUserService.requirePrincipal()));
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.createFolder(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable UUID projectId,
            @PathVariable UUID folderId,
            @Valid @RequestBody UpdateFolderRequest request) {
        return ResponseEntity.ok(folderService.updateFolder(
                projectId, folderId, request, currentUserService.requirePrincipal()));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID projectId,
            @PathVariable UUID folderId) {
        folderService.deleteFolder(projectId, folderId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
