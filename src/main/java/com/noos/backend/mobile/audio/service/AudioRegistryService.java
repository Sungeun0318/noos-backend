package com.noos.backend.mobile.audio.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.noos.backend.mobile.audio.dto.GeneratedAudioRow;
import com.noos.backend.mobile.audio.dto.ResolvedAudio;
import com.noos.backend.mobile.audio.mapper.GeneratedAudioMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AudioRegistryService {

    private final GeneratedAudioMapper mapper;
    private final Cache<String, ResolvedAudio> audioResolutionCache;

    public AudioRegistryService(GeneratedAudioMapper mapper, Cache<String, ResolvedAudio> audioResolutionCache) {
        this.mapper = mapper;
        this.audioResolutionCache = audioResolutionCache;
    }

    public String register(String localPath, String sessionId, String mime, Integer durationSec) {
        String audioId = "audio_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        mapper.insert(audioId, sessionId, "local", localPath, mime, durationSec);
        return audioId;
    }

    public ResolvedAudio resolve(String audioId) {
        return audioResolutionCache.get(audioId, this::load);
    }

    private ResolvedAudio load(String audioId) {
        GeneratedAudioRow row = mapper.findById(audioId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AUDIO_NOT_FOUND");
        }

        LocalDateTime expiresAt = row.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "AUDIO_EXPIRED");
        }

        return new ResolvedAudio(row.getStorageRef(), row.getMime(), row.getDurationSec());
    }
}
