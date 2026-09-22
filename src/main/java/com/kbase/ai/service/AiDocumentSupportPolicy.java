package com.kbase.ai.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/** KBase-owned extension policy; it is intentionally independent of Core file kind. */
@Component
public class AiDocumentSupportPolicy {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "md", "txt");

    public boolean supports(String extension) {
        return extension != null
                && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
}
