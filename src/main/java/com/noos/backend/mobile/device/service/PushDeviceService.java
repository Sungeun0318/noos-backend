package com.noos.backend.mobile.device.service;

import com.noos.backend.mobile.device.dto.PushDeviceRow;
import com.noos.backend.mobile.device.dto.RegisterPushTokenRequest;
import com.noos.backend.mobile.device.dto.RegisterPushTokenResponse;
import com.noos.backend.mobile.device.mapper.PushDeviceMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PushDeviceService {

    private final PushDeviceMapper pushDeviceMapper;

    public PushDeviceService(PushDeviceMapper pushDeviceMapper) {
        this.pushDeviceMapper = pushDeviceMapper;
    }

    public RegisterPushTokenResponse register(String deviceId, Long userId, RegisterPushTokenRequest request) {
        String id = "dreg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Instant now = Instant.now();

        PushDeviceRow row = new PushDeviceRow();
        row.setId(id);
        row.setDeviceId(deviceId);
        row.setUserId(userId);
        row.setPlatform(request.platform());
        row.setProvider(request.provider());
        row.setToken(request.token());
        row.setAppVersion(request.appVersion());
        row.setLocale(request.locale());
        row.setActive(true);
        row.setLastSeenAt(now);
        row.setCreatedAt(now);

        pushDeviceMapper.upsert(row);
        return new RegisterPushTokenResponse(true, id);
    }

    public void unregister(String deviceId) {
        pushDeviceMapper.deactivateByDeviceId(deviceId);
    }
}
