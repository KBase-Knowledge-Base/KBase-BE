package com.kbase.shared.util;

/**
 * Generic LIKE-pattern helper so client supplied search text matches
 * literally instead of acting as SQL wildcards. Contains no domain logic.
 */
public final class LikePatterns {

    /** Escape character declared alongside every escaped LIKE predicate. */
    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** Escapes backslash, percent and underscore for use inside a LIKE pattern. */
    public static String escape(String value) {
        String escaped = value.replace(String.valueOf(ESCAPE), String.valueOf(ESCAPE) + ESCAPE);
        escaped = escaped.replace("%", ESCAPE + "%");
        return escaped.replace("_", ESCAPE + "_");
    }
}
