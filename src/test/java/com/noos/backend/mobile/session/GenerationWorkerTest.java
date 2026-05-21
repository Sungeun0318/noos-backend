package com.noos.backend.mobile.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.audio.dto.GeneratedAudioRow;
import com.noos.backend.mobile.audio.mapper.GeneratedAudioMapper;
import com.noos.backend.mobile.session.dto.MobileSessionRow;
import com.noos.backend.mobile.session.mapper.MobileSessionMapper;
import com.noos.backend.mobile.session.service.GenerationContext;
import com.noos.backend.mobile.session.service.GenerationWorker;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class GenerationWorkerTest {

    @Autowired
    private GenerationWorker worker;

    @Autowired
    private MobileSessionMapper sessionMapper;

    @Autowired
    private GeneratedAudioMapper generatedAudioMapper;

    @MockBean
    private NoosAiService noosAiService;

    @MockBean
    private WizLightingService wizLightingService;

    @TempDir
    private Path tempDir;

    @Test
    void workerMarksSessionReadyAndRegistersGeneratedAudio() throws Exception {
        Path audioFile = createAudioFile();
        String sessionId = "session_worker_" + System.nanoTime();
        insertQueuedSession(sessionId);

        when(noosAiService.generateIntervention(any(InterventionGenerationRequest.class)))
                .thenReturn(Map.of(
                        "interventionResult", Map.of(
                                "ace_step_job", Map.of(
                                        "parsed_entries", List.of(Map.of(
                                                "file", "http://ace-step/audio?path=" + URLEncoder.encode(audioFile.toString(), StandardCharsets.UTF_8)
                                        ))
                                )
                        ),
                        "audioDurationSec", 60,
                        "wizLighting", Map.of("jobId", "lighting_test")
                ));

        worker.run(sessionId, new GenerationContext(
                "Mars",
                60,
                Map.of("focus_readiness", 0.5),
                "calm focus",
                "집중",
                "hybrid",
                true
        ));

        MobileSessionRow row = waitForReady(sessionId);
        assertThat(row.getStatus()).isEqualTo("ready");
        assertThat(row.getAudioId()).startsWith("audio_");
        assertThat(row.getLightingJobId()).isEqualTo("lighting_test");

        GeneratedAudioRow audioRow = generatedAudioMapper.findById(row.getAudioId());
        assertThat(audioRow).isNotNull();
        assertThat(audioRow.getSessionId()).isEqualTo(sessionId);
        assertThat(audioRow.getStorageRef()).isEqualTo(audioFile.toString());
    }

    @Test
    void lightingDisabledStopsActiveJobAfterGeneration() throws Exception {
        Path audioFile = createAudioFile();
        String sessionId = "session_worker_" + System.nanoTime();
        insertQueuedSession(sessionId, false);
        mockInterventionResult(audioFile);

        worker.run(sessionId, new GenerationContext(
                "Mars",
                60,
                Map.of("focus_readiness", 0.5),
                "calm focus",
                "집중",
                "hybrid",
                false
        ));

        MobileSessionRow row = waitForReady(sessionId);
        verify(wizLightingService, times(1)).stopActiveJob();
        assertThat(row.getLightingJobId()).isNull();
    }

    @Test
    void lightingEnabledKeepsActiveJob() throws Exception {
        Path audioFile = createAudioFile();
        String sessionId = "session_worker_" + System.nanoTime();
        insertQueuedSession(sessionId, true);
        mockInterventionResult(audioFile);

        worker.run(sessionId, new GenerationContext(
                "Mars",
                60,
                Map.of("focus_readiness", 0.5),
                "calm focus",
                "집중",
                "hybrid",
                true
        ));

        MobileSessionRow row = waitForReady(sessionId);
        verify(wizLightingService, never()).stopActiveJob();
        assertThat(row.getLightingJobId()).isEqualTo("lighting_test");
    }

    private Path createAudioFile() throws Exception {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        Path audioFile = tempDir.resolve("worker-audio.mp3");
        Files.write(audioFile, data);
        return audioFile;
    }

    private void mockInterventionResult(Path audioFile) {
        when(noosAiService.generateIntervention(any(InterventionGenerationRequest.class)))
                .thenReturn(Map.of(
                        "interventionResult", Map.of(
                                "ace_step_job", Map.of(
                                        "parsed_entries", List.of(Map.of(
                                                "file", "http://ace-step/audio?path=" + URLEncoder.encode(audioFile.toString(), StandardCharsets.UTF_8)
                                        ))
                                )
                        ),
                        "audioDurationSec", 60,
                        "wizLighting", Map.of("jobId", "lighting_test")
                ));
    }

    private void insertQueuedSession(String sessionId) {
        insertQueuedSession(sessionId, true);
    }

    private void insertQueuedSession(String sessionId, boolean lightingEnabled) {
        MobileSessionRow row = new MobileSessionRow();
        row.setId(sessionId);
        row.setDeviceId("dev_generation_worker_test");
        row.setPlanet("Mars");
        row.setDurationSec(60);
        row.setCurrentState("{\"focus_readiness\":0.5}");
        row.setSource("hybrid");
        row.setLightingEnabled(lightingEnabled);
        row.setStatus("queued");
        row.setCreatedAt(Instant.now());
        sessionMapper.insertQueued(row);
    }

    private MobileSessionRow waitForReady(String sessionId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MobileSessionRow row = sessionMapper.findById(sessionId);
            if (row != null && "ready".equals(row.getStatus())) {
                return row;
            }
            Thread.sleep(100);
        }
        return sessionMapper.findById(sessionId);
    }
}
