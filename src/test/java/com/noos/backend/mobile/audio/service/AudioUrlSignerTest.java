package com.noos.backend.mobile.audio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AudioUrlSignerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void signAndVerifyRoundTrip() {
        AudioUrlSigner signer = signer();

        AudioUrlSigner.Signature signature = signer.sign("audio_test");

        assertThat(signature.exp()).isEqualTo(Instant.parse("2026-06-14T12:00:00Z").getEpochSecond());
        assertThat(signer.verify("audio_test", signature.exp(), signature.sig())).isTrue();
        assertThat(signer.streamPath("audio_test"))
                .startsWith("/api/mobile/audio/audio_test?exp=")
                .contains("&sig=");
    }

    @Test
    void expiredSignatureIsRejected() {
        AudioUrlSigner signer = signer();
        long pastExp = clock.instant().minusSeconds(1).getEpochSecond();
        AudioUrlSigner.Signature signature = signer.sign("audio_test", pastExp);

        assertThat(signer.verify("audio_test", signature.exp(), signature.sig())).isFalse();
    }

    @Test
    void forgedOrMissingSignatureIsRejected() {
        AudioUrlSigner signer = signer();
        AudioUrlSigner.Signature signature = signer.sign("audio_test");

        assertThat(signer.verify("audio_test", signature.exp(), "bad")).isFalse();
        assertThat(signer.verify("audio_test", signature.exp(), null)).isFalse();
        assertThat(signer.verify("audio_test", null, signature.sig())).isFalse();
    }

    @Test
    void blankSecretIsRejectedBeforeSigning() {
        AudioUrlSigner signer = new AudioUrlSigner("", Duration.ofHours(12), clock);

        assertThatThrownBy(() -> signer.streamPath("audio_test"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.AUDIO_SIGNATURE_INVALID);
    }

    private AudioUrlSigner signer() {
        return new AudioUrlSigner("test-secret", Duration.ofHours(12), clock);
    }
}
