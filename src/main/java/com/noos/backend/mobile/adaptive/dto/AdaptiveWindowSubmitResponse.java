package com.noos.backend.mobile.adaptive.dto;

public record AdaptiveWindowSubmitResponse(
        Long windowId,
        SixAxis sixAxis,
        AdaptiveAction adaptiveAction,
        NextSegment nextSegment
) {
    public record SixAxis(
            double focusReadiness,
            double stressLoad,
            double fatigueRisk,
            double relaxationLevel,
            double corticalArousal,
            double mentalWorkload
    ) {
    }

    public record AdaptiveAction(
            String type,
            String reason,
            String label,
            double volumeScale
    ) {
    }

    public record NextSegment(
            Long id,
            int index,
            String status
    ) {
    }
}
