package com.noos.backend.mobile.session.mapper;

import com.noos.backend.mobile.session.dto.SessionFeedbackRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionFeedbackMapper {

    void upsert(SessionFeedbackRow row);

    SessionFeedbackRow findBySessionId(@Param("sessionId") String sessionId);
}
