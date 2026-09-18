package com.kbase.shared.pagination;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stable pagination envelope shared by all list endpoints.
 *
 * @param <T> mapped item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,

        @JsonProperty("first")
        boolean first,

        @JsonProperty("last")
        boolean last) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /** Maps a repository page to the standard envelope with mapped items. */
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
