package com.noos.backend.mobile.audio.controller;

import com.noos.backend.mobile.audio.dto.ResolvedAudio;
import com.noos.backend.mobile.audio.service.AudioRegistryService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MobileAudioController {

    private final AudioRegistryService registry;

    public MobileAudioController(AudioRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/api/mobile/audio/{audioId}")
    public ResponseEntity<Resource> stream(@PathVariable String audioId) {
        ResolvedAudio resolved = registry.resolve(audioId);
        Path path = Path.of(resolved.storageRef());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AUDIO_FILE_NOT_FOUND");
        }

        FileSystemResource resource = new FileSystemResource(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resolved.mime()));
        headers.set(HttpHeaders.CACHE_CONTROL, "private,max-age=3600");
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
