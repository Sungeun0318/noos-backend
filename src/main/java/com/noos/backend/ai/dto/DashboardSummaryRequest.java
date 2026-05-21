package com.noos.backend.ai.dto;

import java.util.List;
import java.util.Map;

public record DashboardSummaryRequest(
        List<Map<String, Object>> feedbackHistory,
        String memoText,
        Map<String, Object> currentState
) {
}
