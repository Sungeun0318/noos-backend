package com.noos.backend.mobile.adaptive.mapper;

import com.noos.backend.mobile.adaptive.dto.AdaptiveFeedbackRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdaptiveFeedbackMapper {

    void upsert(AdaptiveFeedbackRow row);

    AdaptiveFeedbackRow findBySessionId(@Param("adaptiveSessionId") String adaptiveSessionId);
}
