package com.noos.backend.eeg.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EegRawChunk {
    private Long eegRawChunkId;
    private Long eegSessionId;
    private Integer chunkIndex;
    private Integer sampleRateHz;
    private Long startOffsetMs;
    private Integer chunkSampleCount;
    private String samplesJson;
    private LocalDateTime createdAt;
}
