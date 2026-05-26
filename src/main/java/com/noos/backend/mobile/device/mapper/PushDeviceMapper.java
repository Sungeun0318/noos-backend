package com.noos.backend.mobile.device.mapper;

import com.noos.backend.mobile.device.dto.PushDeviceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PushDeviceMapper {

    void upsert(PushDeviceRow row);

    void deactivateByDeviceId(@Param("deviceId") String deviceId);

    List<PushDeviceRow> findActive(@Param("deviceId") String deviceId, @Param("userId") Long userId);

    void deactivateById(@Param("id") String id);
}
