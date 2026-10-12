package com.skill2job.dto;

import java.util.List;

/** Our own page JSON (Spring's PageImpl JSON format is not stable). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, pages);
    }
}
