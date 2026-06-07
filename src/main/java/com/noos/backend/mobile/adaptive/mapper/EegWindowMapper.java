package com.noos.backend.mobile.adaptive.mapper;

import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EegWindowMapper {

    void insert(EegWindowRow row);

    EegWindowRow findById(@Param("id") Long id);

    List<EegWindowRow> listWindows(@Param("adaptiveSessionId") String adaptiveSessionId);

    EegWindowRow findLatestWindow(@Param("adaptiveSessionId") String adaptiveSessionId);
}
