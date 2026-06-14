package com.noos.backend.mobile.audio.service;

import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AudioUrlSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final String secret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public AudioUrlSigner(@Value("${noos.mobile.audio.sign-secret:}") String secret,
                          @Value("${noos.mobile.audio.sign-ttl-sec:43200}") long ttlSec) {
        this(secret, Duration.ofSeconds(Math.max(1L, ttlSec)), Clock.systemUTC());
    }

    AudioUrlSigner(String secret, Duration ttl, Clock clock) {
        this.secret = secret == null ? "" : secret;
        this.ttl = ttl;
        this.clock = clock;
    }

    public String streamPath(String audioId) {
        Signature signature = sign(audioId);
        return "/api/mobile/audio/" + audioId + "?exp=" + signature.exp() + "&sig=" + signature.sig();
    }

    public boolean verify(String audioId, Long exp, String sig) {
        if (audioId == null || audioId.isBlank() || exp == null || sig == null || sig.isBlank()) {
            return false;
        }
        if (clock.instant().getEpochSecond() >= exp) {
            return false;
        }
        Signature expected = sign(audioId, exp);
        return MessageDigest.isEqual(
                expected.sig().getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8)
        );
    }

    Signature sign(String audioId) {
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return sign(audioId, exp);
    }

    Signature sign(String audioId, long exp) {
        if (audioId == null || audioId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST);
        }
        return new Signature(exp, hmac(audioId + ":" + exp));
    }

    private String hmac(String payload) {
        if (secret.isBlank()) {
            throw new ApiException(ErrorCode.AUDIO_SIGNATURE_INVALID, "audio signing secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HEX.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ApiException(ErrorCode.INTERNAL, "audio signing failed", e);
        }
    }

    record Signature(long exp, String sig) {
    }
}
