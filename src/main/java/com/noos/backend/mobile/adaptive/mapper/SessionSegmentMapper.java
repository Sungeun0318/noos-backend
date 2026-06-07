package com.noos.backend.mobile.adaptive.mapper;

import com.noos.backend.mobile.adaptive.dto.SessionSegmentRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionSegmentMapper {

    void insert(SessionSegmentRow row);

    SessionSegmentRow findById(@Param("id") Long id);

    List<SessionSegmentRow> listSegments(@Param("adaptiveSessionId") String adaptiveSessionId);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("audioId") String audioId,
                      @Param("genStartedAt") Instant genStartedAt,
                      @Param("genReadyAt") Instant genReadyAt,
                      @Param("playedAt") Instant playedAt,
                      @Param("errorCode") String errorCode);
}
