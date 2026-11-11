package com.txnscreen.api;

import java.util.List;

/**
 * One page of a list.
 *
 * @param items the items on this page, empty past the last page
 * @param page the 0-based page number
 * @param size the page size asked for
 * @param totalItems the number of items across all pages
 * @param totalPages the number of pages
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    /**
     * Cuts one page out of a full list.
     *
     * @param all every item, in order
     * @param page the 0-based page number
     * @param size the page size, at least 1
     * @return the page
     */
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (all.size() + size - 1) / size;
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }
}
