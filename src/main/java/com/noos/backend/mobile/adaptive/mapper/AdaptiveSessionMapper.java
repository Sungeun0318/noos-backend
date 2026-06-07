package com.noos.backend.mobile.adaptive.mapper;

import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionRow;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdaptiveSessionMapper {

    void insert(AdaptiveSessionRow row);

    AdaptiveSessionRow findById(@Param("id") String id);

    void updateStatus(@Param("id") String id,
                      @Param("status") String status,
                      @Param("pausedReason") String pausedReason,
                      @Param("pausedAt") Instant pausedAt,
                      @Param("endedAt") Instant endedAt);
}
