package com.noos.backend.mobile.session.dto;

import java.util.List;

public record SessionListResponse(
        List<SessionResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
