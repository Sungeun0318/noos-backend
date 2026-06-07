package com.noos.backend.mobile.adaptive.service;

import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitResponse;
import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveActionResolver {

    private static final double LOW_QUALITY_THRESHOLD = 0.35;
    private static final Set<String> CALM_PLANETS = Set.of("Venus", "Earth", "Pluto");
    private static final Set<String> FOCUS_PLANETS = Set.of("Mercury", "Mars", "Jupiter", "Neptune");

    public AdaptiveWindowSubmitResponse.AdaptiveAction resolve(AdaptiveWindowSubmitResponse.SixAxis currentState,
                                                               EegWindowRow previousWindow,
                                                               List<EegWindowRow> windows,
                                                               boolean signalOk,
                                                               Double qualityScore,
                                                               String planet,
                                                               Instant now,
                                                               Duration minRegenInterval) {
        if (!signalOk || (qualityScore != null && qualityScore < LOW_QUALITY_THRESHOLD)) {
            return none("low-signal-quality", "신호 품질이 낮아 음악을 유지합니다.");
        }

        AdaptiveWindowSubmitResponse.SixAxis previousState = previousWindow == null
                ? neutral()
                : sixAxis(previousWindow);
        double focusDelta = clamp(currentState.focusReadiness()) - clamp(previousState.focusReadiness());
        double stressDelta = clamp(currentState.stressLoad()) - clamp(previousState.stressLoad());
        double fatigueDelta = clamp(currentState.fatigueRisk()) - clamp(previousState.fatigueRisk());
        double relaxationDelta = clamp(currentState.relaxationLevel()) - clamp(previousState.relaxationLevel());
        double movement = Math.abs(focusDelta) + Math.abs(stressDelta)
                + Math.abs(fatigueDelta) + Math.abs(relaxationDelta);
        String mode = adaptationMode(planet);
        double stress = clamp(currentState.stressLoad());
        double fatigue = clamp(currentState.fatigueRisk());
        double focus = clamp(currentState.focusReadiness());
        double relaxation = clamp(currentState.relaxationLevel());

        if (movement < 0.16) {
            return none("stable-state", "상태 변화가 작아 현재 음악을 유지합니다.");
        }

        boolean shouldChangeTrack = movement >= 0.42
                || stress >= 0.72
                || fatigue >= 0.74
                || ("focus".equals(mode) && focus < 0.38)
                || ("calm".equals(mode) && relaxation < 0.36);

        if (shouldChangeTrack) {
            AdaptiveWindowSubmitResponse.AdaptiveAction crossfade = crossfade(mode, stress, fatigue);
            if (withinMinRegenInterval(windows, now, minRegenInterval)) {
                return new AdaptiveWindowSubmitResponse.AdaptiveAction(
                        "parameter_adjust",
                        "regen-throttled",
                        "최근 전환 직후라 현재 트랙을 우선 조정합니다.",
                        crossfade.volumeScale() < 1.0 ? 0.92 : 1.06
                );
            }
            return crossfade;
        }

        if ("calm".equals(mode) || stressDelta > 0.08) {
            return new AdaptiveWindowSubmitResponse.AdaptiveAction(
                    "parameter_adjust",
                    "soften-current-track",
                    "현재 트랙을 더 부드럽게 조정합니다.",
                    0.92
            );
        }
        return new AdaptiveWindowSubmitResponse.AdaptiveAction(
                "parameter_adjust",
                "energize-current-track",
                "현재 트랙의 에너지를 조금 올립니다.",
                1.06
        );
    }

    private AdaptiveWindowSubmitResponse.AdaptiveAction crossfade(String mode, double stress, double fatigue) {
        boolean calmer = stress >= 0.72 || fatigue >= 0.74 || "calm".equals(mode);
        return new AdaptiveWindowSubmitResponse.AdaptiveAction(
                "crossfade",
                calmer ? "calmer-crossfade" : "focus-crossfade",
                calmer
                        ? "긴장/피로 신호가 커져 더 차분한 음악으로 전환합니다."
                        : "집중 신호를 보강하기 위해 새 음악으로 전환합니다.",
                calmer ? 0.88 : 1.04
        );
    }

    private boolean withinMinRegenInterval(List<EegWindowRow> windows, Instant now, Duration minRegenInterval) {
        if (windows == null || minRegenInterval == null || minRegenInterval.isZero() || minRegenInterval.isNegative()) {
            return false;
        }
        return windows.stream()
                .filter(window -> "crossfade".equals(window.getAdaptiveAction()))
                .map(EegWindowRow::getCreatedAt)
                .filter(createdAt -> createdAt != null && !createdAt.isAfter(now))
                .max(Instant::compareTo)
                .map(lastCrossfadeAt -> Duration.between(lastCrossfadeAt, now).compareTo(minRegenInterval) < 0)
                .orElse(false);
    }

    private String adaptationMode(String planet) {
        if (CALM_PLANETS.contains(planet)) {
            return "calm";
        }
        if (FOCUS_PLANETS.contains(planet)) {
            return "focus";
        }
        if ("Saturn".equals(planet)) {
            return "deep";
        }
        return "balanced";
    }

    private AdaptiveWindowSubmitResponse.AdaptiveAction none(String reason, String label) {
        return new AdaptiveWindowSubmitResponse.AdaptiveAction("none", reason, label, 1.0);
    }

    private AdaptiveWindowSubmitResponse.SixAxis neutral() {
        return new AdaptiveWindowSubmitResponse.SixAxis(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
    }

    private AdaptiveWindowSubmitResponse.SixAxis sixAxis(EegWindowRow row) {
        return new AdaptiveWindowSubmitResponse.SixAxis(
                clamp(row.getFocusReadiness()),
                clamp(row.getStressLoad()),
                clamp(row.getFatigueRisk()),
                clamp(row.getRelaxationLevel()),
                clamp(row.getCorticalArousal()),
                clamp(row.getMentalWorkload())
        );
    }

    private double clamp(Double value) {
        return clamp(value == null ? 0.0 : value);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
