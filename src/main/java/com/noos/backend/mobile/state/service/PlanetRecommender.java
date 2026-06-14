package com.noos.backend.mobile.state.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PlanetRecommender {

    private static final List<String> AXES = List.of(
            "focus_readiness",
            "stress_load",
            "fatigue_risk",
            "relaxation_level",
            "cortical_arousal",
            "mental_workload"
    );

    // 웹 noos_ai/intervention/planet_profiles.py 와 동기화 — 단일원은 웹.
    private static final List<PlanetTarget> PLANETS = List.of(
            target("Mercury", 0.82, 0.28, 0.18, 0.32, 0.78, 0.56),
            target("Venus", 0.58, 0.22, 0.30, 0.64, 0.47, 0.34),
            target("Earth", 0.72, 0.22, 0.25, 0.54, 0.52, 0.48),
            target("Mars", 0.84, 0.42, 0.16, 0.18, 0.86, 0.62),
            target("Jupiter", 0.74, 0.32, 0.18, 0.38, 0.72, 0.55),
            target("Saturn", 0.76, 0.18, 0.28, 0.62, 0.38, 0.58),
            target("Uranus", 0.60, 0.26, 0.20, 0.46, 0.60, 0.45),
            target("Neptune", 0.88, 0.14, 0.20, 0.58, 0.40, 0.64),
            target("Pluto", 0.22, 0.08, 0.20, 0.88, 0.16, 0.12)
    );

    public Recommendation recommend(Map<String, Double> currentState) {
        Map<String, Double> state = normalizeState(currentState);
        double focus = state.get("focus_readiness");
        double stress = state.get("stress_load");
        double fatigue = state.get("fatigue_risk");
        double relaxation = state.get("relaxation_level");
        double arousal = state.get("cortical_arousal");
        double workload = state.get("mental_workload");

        double recoveryPressure = averagePositive(
                pressureAbove(stress, 0.6),
                pressureAbove(fatigue, 0.6),
                pressureAbove(arousal, 0.6),
                pressureAbove(workload, 0.6)
        );
        double focusPressure = stress <= 0.65 ? pressureBelow(focus, 0.4) : 0.0;
        double relaxationPressure = pressureBelow(relaxation, 0.55);

        List<ScoredPlanet> ranked = PLANETS.stream()
                .map(planet -> new ScoredPlanet(planet, score(planet, recoveryPressure, focusPressure, relaxationPressure)))
                .sorted(Comparator.comparingDouble(ScoredPlanet::score).reversed())
                .toList();

        ScoredPlanet winner = ranked.get(0);
        List<String> alternates = ranked.stream()
                .skip(1)
                .limit(2)
                .map(scored -> scored.planet().name())
                .toList();
        double confidence = clamp(winner.score());
        return new Recommendation(winner.planet().name(), alternates, confidence);
    }

    private double score(PlanetTarget planet,
                         double recoveryPressure,
                         double focusPressure,
                         double relaxationPressure) {
        double recoveryFit = (1.0 - planet.stress()) * 0.32
                + (1.0 - planet.fatigue()) * 0.22
                + (1.0 - planet.arousal()) * 0.24
                + planet.relaxation() * 0.22;
        double activationFit = planet.focus() * 0.45
                + planet.arousal() * 0.35
                + planet.workload() * 0.20;
        double balancedFit = planet.focus() * 0.32
                + (1.0 - planet.stress()) * 0.22
                + (1.0 - planet.fatigue()) * 0.18
                + planet.relaxation() * 0.18
                + (1.0 - Math.abs(planet.arousal() - 0.52)) * 0.10;

        double weightedPressure = Math.max(recoveryPressure, Math.max(focusPressure, relaxationPressure));
        if (weightedPressure == 0.0) {
            return balancedFit;
        }

        return (balancedFit * 0.10)
                + (recoveryFit * (recoveryPressure + relaxationPressure))
                + (activationFit * focusPressure * 1.45);
    }

    private Map<String, Double> normalizeState(Map<String, Double> currentState) {
        Map<String, Double> state = new LinkedHashMap<>();
        for (String axis : AXES) {
            Double value = currentState == null ? null : currentState.get(axis);
            state.put(axis, value == null ? 0.5 : clamp(value));
        }
        return state;
    }

    private static PlanetTarget target(String name,
                                       double focus,
                                       double stress,
                                       double fatigue,
                                       double relaxation,
                                       double arousal,
                                       double workload) {
        return new PlanetTarget(name, focus, stress, fatigue, relaxation, arousal, workload);
    }

    private double pressureAbove(double value, double threshold) {
        if (value < threshold) {
            return 0.0;
        }
        return Math.max(0.05, clamp((value - threshold) / (1.0 - threshold)));
    }

    private double pressureBelow(double value, double threshold) {
        return clamp((threshold - value) / threshold);
    }

    private double averagePositive(double... values) {
        double total = 0.0;
        int count = 0;
        for (double value : values) {
            if (value > 0.0) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Recommendation(String recommendedPlanet, List<String> alternates, double confidence) {
    }

    private record PlanetTarget(String name,
                                double focus,
                                double stress,
                                double fatigue,
                                double relaxation,
                                double arousal,
                                double workload) {
    }

    private record ScoredPlanet(PlanetTarget planet, double score) {
    }
}
