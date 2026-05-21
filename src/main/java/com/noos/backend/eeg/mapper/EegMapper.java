package com.noos.backend.eeg.mapper;

import com.noos.backend.eeg.dto.EegResult;
import com.noos.backend.eeg.dto.EegRawChunk;
import com.noos.backend.eeg.dto.EegSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EegMapper {

    void insertEegSession(EegSession eegSession);

    EegSession selectEegSessionById(@Param("eegSessionId") Long eegSessionId);

    void updateEegSessionStatus(@Param("eegSessionId") Long eegSessionId, @Param("status") String status);

    void insertEegResult(EegResult eegResult);

    void insertEegRawChunk(EegRawChunk eegRawChunk);

    List<EegRawChunk> selectEegRawChunksBySessionId(@Param("eegSessionId") Long eegSessionId);
}
