package com.noos.backend.eeg.dto;

import java.util.List;

public record EegRawChunkUploadRequest(
        Long eegSessionId,
        Integer sampleRateHz,
        Integer chunkIndex,
        Long startOffsetMs,
        List<List<Double>> samples
) {
}
