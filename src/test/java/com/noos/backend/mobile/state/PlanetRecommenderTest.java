package com.noos.backend.mobile.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.noos.backend.mobile.state.service.PlanetRecommender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanetRecommenderTest {

    private final PlanetRecommender recommender = new PlanetRecommender();

    @Test
    void highStressAndFatiguePreferRecoveryPlanet() {
        PlanetRecommender.Recommendation recommendation = recommender.recommend(Map.of(
                "focus_readiness", 0.42,
                "stress_load", 0.82,
                "fatigue_risk", 0.78,
                "relaxation_level", 0.24,
                "cortical_arousal", 0.70,
                "mental_workload", 0.74
        ));

        assertThat(List.of("Pluto", "Saturn", "Venus"))
                .contains(recommendation.recommendedPlanet());
        assertThat(recommendation.alternates()).hasSize(2);
        assertThat(recommendation.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void lowFocusWithAcceptableStressPrefersActivationPlanet() {
        PlanetRecommender.Recommendation recommendation = recommender.recommend(Map.of(
                "focus_readiness", 0.24,
                "stress_load", 0.32,
                "fatigue_risk", 0.28,
                "relaxation_level", 0.48,
                "cortical_arousal", 0.40,
                "mental_workload", 0.35
        ));

        assertThat(List.of("Mercury", "Mars", "Jupiter"))
                .contains(recommendation.recommendedPlanet());
        assertThat(recommendation.alternates()).hasSize(2);
    }

    @Test
    void elevatedBoundaryIncludesPointSixStress() {
        PlanetRecommender.Recommendation recommendation = recommender.recommend(Map.of(
                "focus_readiness", 0.52,
                "stress_load", 0.60,
                "fatigue_risk", 0.62,
                "relaxation_level", 0.42,
                "cortical_arousal", 0.60,
                "mental_workload", 0.58
        ));

        assertThat(List.of("Pluto", "Saturn", "Venus", "Neptune"))
                .contains(recommendation.recommendedPlanet());
        assertThat(recommendation.confidence()).isBetween(0.0, 1.0);
    }
}
