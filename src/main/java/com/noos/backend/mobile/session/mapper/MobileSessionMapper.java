package com.noos.backend.mobile.session.mapper;

import com.noos.backend.mobile.session.dto.MobileSessionRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MobileSessionMapper {

    void insertQueued(MobileSessionRow row);

    void updateStatus(@Param("id") String id, @Param("status") String status, @Param("startedAt") Instant startedAt);

    void markReady(@Param("id") String id,
                   @Param("audioId") String audioId,
                   @Param("lightingJobId") String lightingJobId,
                   @Param("completedAt") Instant completedAt);

    void markFailed(@Param("id") String id, @Param("code") String code, @Param("message") String message);

    MobileSessionRow findById(@Param("id") String id);

    List<MobileSessionRow> list(@Param("deviceId") String deviceId,
                                @Param("userId") Long userId,
                                @Param("cursor") String cursor,
                                @Param("limit") int limit,
                                @Param("status") List<String> status);

    void softDelete(@Param("id") String id);
}
