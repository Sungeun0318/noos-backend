package com.noos.backend.mobile.auth.service;

import com.noos.backend.mobile.device.mapper.PushDeviceMapper;
import com.noos.backend.mobile.session.mapper.MobileSessionMapper;
import com.noos.backend.mobile.state.mapper.StateMeasurementMapper;
import org.springframework.stereotype.Service;

@Service
public class ClaimService {

    private final MobileSessionMapper mobileSessionMapper;
    private final StateMeasurementMapper stateMeasurementMapper;
    private final PushDeviceMapper pushDeviceMapper;

    public ClaimService(MobileSessionMapper mobileSessionMapper,
                        StateMeasurementMapper stateMeasurementMapper,
                        PushDeviceMapper pushDeviceMapper) {
        this.mobileSessionMapper = mobileSessionMapper;
        this.stateMeasurementMapper = stateMeasurementMapper;
        this.pushDeviceMapper = pushDeviceMapper;
    }

    public ClaimResult claim(long userId, String deviceId) {
        int sessions = mobileSessionMapper.attachUserIdByDevice(userId, deviceId);
        int measurements = stateMeasurementMapper.attachUserIdByDevice(userId, deviceId);
        pushDeviceMapper.attachUserIdByDevice(userId, deviceId);
        return new ClaimResult(sessions, measurements);
    }

    public record ClaimResult(int sessions, int measurements) {
    }
}
