package com.noos.backend.mobile.device.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.noos.backend.mobile.device.dto.PushDeviceRow;
import com.noos.backend.mobile.device.mapper.PushDeviceMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final PushDeviceMapper pushDeviceMapper;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    public NotificationService(PushDeviceMapper pushDeviceMapper,
                               ObjectProvider<FirebaseMessaging> firebaseMessagingProvider) {
        this.pushDeviceMapper = pushDeviceMapper;
        this.firebaseMessagingProvider = firebaseMessagingProvider;
    }

    public void notifySessionReady(String deviceId, Long userId, String sessionId, String planet) {
        notifySession(deviceId, userId, sessionId, planet, "session_ready");
    }

    public void notifySessionFailed(String deviceId, Long userId, String sessionId, String planet) {
        notifySession(deviceId, userId, sessionId, planet, "session_failed");
    }

    private void notifySession(String deviceId, Long userId, String sessionId, String planet, String type) {
        FirebaseMessaging fcm = firebaseMessagingProvider.getIfAvailable();
        if (fcm == null) {
            log.info("push disabled: would notify {} {}", type, sessionId);
            return;
        }

        Map<String, String> payload = payload(type, sessionId, planet);
        for (PushDeviceRow target : pushDeviceMapper.findActive(deviceId, userId)) {
            if ("fcm".equals(target.getProvider())) {
                sendFcm(fcm, target, payload);
            }
        }
    }

    private void sendFcm(FirebaseMessaging fcm, PushDeviceRow target, Map<String, String> payload) {
        Message message = Message.builder()
                .setToken(target.getToken())
                .putAllData(payload)
                .build();
        try {
            fcm.send(message);
        } catch (Exception e) {
            log.warn("push failed: deviceRegistrationId={}", target.getId(), e);
        }
    }

    private Map<String, String> payload(String type, String sessionId, String planet) {
        if ("session_failed".equals(type)) {
            return Map.of(
                    "type", type,
                    "sessionId", sessionId,
                    "title", "음악 생성에 실패했어요",
                    "body", "다시 시도하거나 큐레이션 트랙을 들어보세요.",
                    "deepLink", "noos://session/" + sessionId
            );
        }

        return Map.of(
                "type", type,
                "sessionId", sessionId,
                "planet", planet == null ? "" : planet,
                "title", (planet == null ? "NOOS" : planet) + " 세션이 준비됐어요",
                "body", "탭하면 바로 재생을 시작합니다.",
                "deepLink", "noos://session/" + sessionId
        );
    }
}
