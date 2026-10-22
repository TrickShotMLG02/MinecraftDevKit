package com.trickshotmlg.devkit.bukkit.gui;

/** Immutable, one-based pagination information for display and controls. */
public record PageInfo(int currentPage, int totalPages, int totalItems, int pageSize,
                       int firstVisibleItemIndex, int visibleItemCount,
                       boolean hasPrevious, boolean hasNext) {
    public PageInfo {
        if (currentPage < 1 || totalPages < 1 || totalItems < 0 || pageSize < 1
                || firstVisibleItemIndex < 0 || visibleItemCount < 0 || visibleItemCount > pageSize)
            throw new IllegalArgumentException("Invalid pagination metadata");
    }
    public static PageInfo of(int requestedPage, int totalItems, int pageSize) {
        if (requestedPage < 1) throw new IllegalArgumentException("Pages are one-based and must be positive");
        if (totalItems < 0 || pageSize < 1) throw new IllegalArgumentException("totalItems must be non-negative and pageSize positive");
        int pages = Math.max(1, (int) (((long) totalItems + pageSize - 1) / pageSize));
        int page = Math.min(requestedPage, pages);
        int start = Math.min(totalItems, (page - 1) * pageSize);
        int count = Math.min(pageSize, totalItems - start);
        return new PageInfo(page, pages, totalItems, pageSize, start, count, page > 1, page < pages);
    }
}
