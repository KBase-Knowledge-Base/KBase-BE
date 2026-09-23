package com.kbase.ai.extraction;

/** KBase-owned source provenance; parser-library location types never cross this boundary. */
public record SourceLocation(
        Integer pageNumber,
        Integer slideNumber,
        String sectionTitle) {

    public SourceLocation {
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (slideNumber != null && slideNumber <= 0) {
            throw new IllegalArgumentException("slideNumber must be positive");
        }
        sectionTitle = normalizeTitle(sectionTitle);
    }

    public static SourceLocation none() {
        return new SourceLocation(null, null, null);
    }

    public boolean isEmpty() {
        return pageNumber == null && slideNumber == null && sectionTitle == null;
    }

    private static String normalizeTitle(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        // The V4 column is VARCHAR(255). Truncation is deterministic and keeps
        // an untrusted heading from causing a late database failure.
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
}
