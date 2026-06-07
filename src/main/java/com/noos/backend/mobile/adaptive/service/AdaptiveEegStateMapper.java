package com.noos.backend.mobile.adaptive.service;

import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitResponse;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveEegStateMapper {

    public AdaptiveWindowSubmitResponse.SixAxis fromBands(AdaptiveWindowSubmitRequest.Bands bands) {
        double alpha = band(bands == null ? null : bands.alpha());
        double beta = band(bands == null ? null : bands.beta());
        double theta = band(bands == null ? null : bands.theta());
        double delta = band(bands == null ? null : bands.delta());
        double gamma = band(bands == null ? null : bands.gamma());

        double focus = clamp(beta * 0.45 + alpha * 0.2 + (1.0 - theta) * 0.2 + (1.0 - gamma) * 0.15);
        double stress = clamp(beta * 0.55 + gamma * 0.2 + (1.0 - alpha) * 0.25);
        double fatigue = clamp(theta * 0.45 + delta * 0.2 + alpha * 0.2 + (1.0 - beta) * 0.15);
        double relaxation = clamp(alpha * 0.5 + (1.0 - beta) * 0.3 + (1.0 - gamma) * 0.2);

        // StateMeasurementService 와 동일한 파생 공식.
        double corticalArousal = clamp((focus + (1.0 - relaxation)) / 2.0);
        double mentalWorkload = clamp((stress + fatigue) / 2.0);

        return new AdaptiveWindowSubmitResponse.SixAxis(
                focus,
                stress,
                fatigue,
                relaxation,
                corticalArousal,
                mentalWorkload
        );
    }

    public String stateLabel(AdaptiveWindowSubmitResponse.SixAxis state) {
        if (state.stressLoad() > 0.7) {
            return "stressed";
        }
        if (state.fatigueRisk() > 0.7) {
            return "fatigued";
        }
        if (state.relaxationLevel() > 0.6 && state.focusReadiness() > 0.5) {
            return "calm focus";
        }
        if (state.focusReadiness() > 0.7) {
            return "focused";
        }
        return "neutral";
    }

    private double band(Double value) {
        return clamp(value == null ? 0.0 : value);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
