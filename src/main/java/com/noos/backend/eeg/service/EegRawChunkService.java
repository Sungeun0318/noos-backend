package com.noos.backend.eeg.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.eeg.dto.EegRawChunk;
import com.noos.backend.eeg.dto.EegRawChunkUploadRequest;
import com.noos.backend.eeg.dto.EegRawChunkUploadResponse;
import com.noos.backend.eeg.mapper.EegMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EegRawChunkService {

    private static final Logger logger = LoggerFactory.getLogger(EegRawChunkService.class);

    private final EegMapper eegMapper;
    private final ObjectMapper objectMapper;

    public EegRawChunkService(EegMapper eegMapper, ObjectMapper objectMapper) {
        this.eegMapper = eegMapper;
        this.objectMapper = objectMapper;
    }

    public EegRawChunkUploadResponse saveChunk(EegRawChunkUploadRequest request) {
        if (request.eegSessionId() == null || request.eegSessionId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eegSessionId is required");
        }
        if (request.chunkIndex() == null || request.chunkIndex() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkIndex must be zero or greater");
        }
        if (request.samples() == null || request.samples().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "samples are required");
        }

        EegRawChunk chunk = new EegRawChunk();
        chunk.setEegSessionId(request.eegSessionId());
        chunk.setChunkIndex(request.chunkIndex());
        chunk.setSampleRateHz(request.sampleRateHz() != null ? request.sampleRateHz() : 256);
        chunk.setStartOffsetMs(request.startOffsetMs() != null ? request.startOffsetMs() : 0L);
        chunk.setChunkSampleCount(request.samples().size());
        chunk.setSamplesJson(toJson(request.samples()));

        eegMapper.insertEegRawChunk(chunk);
        logger.info(
                "Saved EEG raw chunk: eegSessionId={}, chunkIndex={}, sampleCount={}",
                chunk.getEegSessionId(),
                chunk.getChunkIndex(),
                chunk.getChunkSampleCount()
        );

        return new EegRawChunkUploadResponse(
                chunk.getEegSessionId(),
                chunk.getChunkIndex(),
                chunk.getChunkSampleCount(),
                true
        );
    }

    public List<EegRawChunk> findChunks(Long eegSessionId) {
        if (eegSessionId == null || eegSessionId <= 0) {
            return List.of();
        }
        return eegMapper.selectEegRawChunksBySessionId(eegSessionId);
    }

    private String toJson(List<List<Double>> samples) {
        try {
            return objectMapper.writeValueAsString(samples);
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to serialize EEG raw chunk", error);
        }
    }

}
