package com.noos.backend.eeg.dto;

public record EegRawChunkUploadResponse(
        Long eegSessionId,
        Integer chunkIndex,
        Integer savedCount,
        boolean saved
) {
}
