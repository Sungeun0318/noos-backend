package com.noos.backend.mobile.audio.mapper;

import com.noos.backend.mobile.audio.dto.GeneratedAudioRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GeneratedAudioMapper {

    void insert(@Param("id") String id,
                @Param("sessionId") String sessionId,
                @Param("storageType") String storageType,
                @Param("storageRef") String storageRef,
                @Param("mime") String mime,
                @Param("durationSec") Integer durationSec);

    GeneratedAudioRow findById(@Param("id") String id);
}
