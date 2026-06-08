package com.noos.backend.mobile.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitRequest;
import com.noos.backend.mobile.adaptive.service.AdaptiveEegStateMapper;
import org.junit.jupiter.api.Test;

class AdaptiveEegStateMapperTest {

    private final AdaptiveEegStateMapper mapper = new AdaptiveEegStateMapper();

    @Test
    void normalizesRawBandPowerBeforeSixAxisMapping() {
        var state = mapper.fromBands(bands(10.0, 30.0, 30.0, 20.0, 10.0));

        assertThat(state.focusReadiness()).isCloseTo(0.425, within(0.0001));
        assertThat(state.stressLoad()).isCloseTo(0.305, within(0.0001));
        assertThat(state.fatigueRisk()).isCloseTo(0.335, within(0.0001));
        assertThat(state.relaxationLevel()).isCloseTo(0.57, within(0.0001));
        assertThat(state.focusReadiness()).isLessThan(1.0);
        assertThat(state.stressLoad()).isLessThan(1.0);
    }

    @Test
    void alphaHeavyInputIsMoreRelaxedThanBetaGammaHeavyInput() {
        var alphaHeavy = mapper.fromBands(bands(5.0, 10.0, 70.0, 10.0, 5.0));
        var betaGammaHeavy = mapper.fromBands(bands(5.0, 10.0, 10.0, 45.0, 30.0));

        assertThat(alphaHeavy.relaxationLevel()).isGreaterThan(betaGammaHeavy.relaxationLevel());
        assertThat(alphaHeavy.stressLoad()).isLessThan(betaGammaHeavy.stressLoad());
    }

    @Test
    void handlesZeroAndSingleBandInputsSafely() {
        var empty = mapper.fromBands(bands(0.0, 0.0, 0.0, 0.0, 0.0));
        var alphaOnly = mapper.fromBands(bands(0.0, 0.0, 5.0, 0.0, 0.0));

        assertThat(empty.focusReadiness()).isCloseTo(0.35, within(0.0001));
        assertThat(empty.relaxationLevel()).isCloseTo(0.5, within(0.0001));
        assertThat(alphaOnly.relaxationLevel()).isCloseTo(1.0, within(0.0001));
        assertThat(alphaOnly.stressLoad()).isCloseTo(0.0, within(0.0001));
    }

    private AdaptiveWindowSubmitRequest.Bands bands(
            Double delta,
            Double theta,
            Double alpha,
            Double beta,
            Double gamma
    ) {
        return new AdaptiveWindowSubmitRequest.Bands(delta, theta, alpha, beta, gamma);
    }
}
