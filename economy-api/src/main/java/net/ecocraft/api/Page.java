package net.ecocraft.api;

import java.util.List;

public record Page<T>(
        List<T> items,
        int offset,
        int limit,
        long totalCount
) {
    public int totalPages() {
        if (limit <= 0) return 0;
        return (int) Math.ceil((double) totalCount / limit);
    }

    public int currentPage() {
        if (limit <= 0) return 0;
        return offset / limit;
    }

    public boolean hasNext() {
        return offset + limit < totalCount;
    }

    public boolean hasPrevious() {
        return offset > 0;
    }
}
