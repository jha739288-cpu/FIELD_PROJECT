package com.labmarket.dto;

import java.util.List;

/** Paginated envelope (see docs/api-standards.md). */
public record PagedResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {}
