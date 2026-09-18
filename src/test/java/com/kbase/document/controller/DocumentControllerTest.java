package com.kbase.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import com.kbase.document.service.DocumentService;
import com.kbase.document.service.FileDelivery;
import com.kbase.document.service.InvalidRangeException;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.exception.ConstraintViolationTranslator;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract tests for M11 streaming headers; service tests own authorization/storage behavior. */
@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConstraintViolationTranslator.class)
class DocumentControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private DocumentService documentService;
    @MockitoBean private CurrentUserService currentUserService;

    @Test
    void mp4RangeReturns206WithRequiredHeadersAndInvalidRangeReturns416() throws Exception {
        UUID id = UUID.randomUUID();
        CustomUserPrincipal principal = new CustomUserPrincipal(UUID.randomUUID(), "viewer@example.com",
                SystemRole.USER, UserStatus.ACTIVE, true);
        when(currentUserService.requirePrincipal()).thenReturn(principal);
        when(documentService.preview(eq(id), eq(principal), eq("bytes=2-4"))).thenReturn(
                new FileDelivery(new ByteArrayInputStream(new byte[] {2, 3, 4}), 3, 10,
                        "video/mp4", "clip.mp4", true, 2, 4));

        mockMvc.perform(get("/api/v1/documents/{id}/preview", id).header("Range", "bytes=2-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 2-4/10"));

        when(documentService.preview(eq(id), eq(principal), eq("bytes=99-100")))
                .thenThrow(new InvalidRangeException(10));
        mockMvc.perform(get("/api/v1/documents/{id}/preview", id).header("Range", "bytes=99-100"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */10"));
    }
}
