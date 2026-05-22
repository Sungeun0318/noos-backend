package com.noos.backend.mobile.state.mapper;

import com.noos.backend.mobile.state.dto.StateMeasurementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StateMeasurementMapper {

    void insert(StateMeasurementRow row);

    StateMeasurementRow findById(@Param("id") String id);
}
