package com.noos.backend.mobile.audio;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.noos.backend.mobile.audio.service.AudioRegistryService;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MobileAudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudioRegistryService registry;

    @TempDir
    private Path tempDir;

    @Test
    void registeredAudioStreamsFileWithoutExposingStorageRef() throws Exception {
        Path audioFile = createAudioFile();
        String audioId = registry.register(audioFile.toString(), "session_test", "audio/mpeg", 60);

        mockMvc.perform(get("/api/mobile/audio/{audioId}", audioId)
                        .header("x-device-id", "dev_test_001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("audio/mpeg")))
                .andExpect(content().bytes(Files.readAllBytes(audioFile)));
    }

    @Test
    void unknownAudioIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/mobile/audio/unknown_id")
                        .header("x-device-id", "dev_test_001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AUDIO_NOT_FOUND"));
    }

    @Test
    void rangeRequestReturnsPartialContent() throws Exception {
        Path audioFile = createAudioFile();
        String audioId = registry.register(audioFile.toString(), "session_test", "audio/mpeg", 60);

        mockMvc.perform(get("/api/mobile/audio/{audioId}", audioId)
                        .header("x-device-id", "dev_test_001")
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-99/1024"))
                .andExpect(content().bytes(Arrays.copyOfRange(Files.readAllBytes(audioFile), 0, 100)));
    }

    private Path createAudioFile() throws Exception {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }

        Path audioFile = tempDir.resolve("test-audio.mp3");
        Files.write(audioFile, data);
        return audioFile;
    }
}
