package com.noos.backend.mobile.common.mapper;

import com.noos.backend.mobile.common.IdempotencyKeyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyKeyMapper {

    IdempotencyKeyRow findByKey(@Param("k") String key);

    void store(IdempotencyKeyRow row);

    int deleteExpired();
}
