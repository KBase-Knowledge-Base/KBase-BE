package com.kbase.shared.pagination;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Parses list-endpoint pagination parameters against the Core v1 baseline:
 * {@code page=0}, {@code size=20}, max size 100, sort whitelist per resource.
 */
@Component
public class PaginationParser {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /**
     * Builds a Pageable from raw query parameters.
     *
     * @param sort        raw {@code field,direction} value; may be null
     * @param allowedSortFields whitelist of sortable fields
     * @param defaultSort applied when sort is absent
     */
    public Pageable parse(Integer page, Integer size, String sort,
            List<String> allowedSortFields, Sort defaultSort) {
        int pageNumber = page == null ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, page);
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));

        Sort requestedSort = parseSort(sort, allowedSortFields);
        return PageRequest.of(pageNumber, pageSize, requestedSort.isUnsorted() ? defaultSort : requestedSort);
    }

    private Sort parseSort(String sort, List<String> allowedSortFields) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        boolean allowed = allowedSortFields.stream()
                .anyMatch(allowedField -> allowedField.equalsIgnoreCase(field));
        if (!allowed) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Sort field is not supported for this resource.");
        }
        return Sort.by(direction, field);
    }

    /** Normalizes a free-text {@code q} filter into a blank-or-lowercase token. */
    public static String normalizeQuery(String q) {
        Objects.requireNonNull(q, "q");
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
