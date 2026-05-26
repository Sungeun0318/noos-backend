package com.noos.backend.mobile.device.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.noos.backend.mobile.device.dto.PushDeviceRow;
import com.noos.backend.mobile.device.mapper.PushDeviceMapper;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final PushDeviceMapper pushDeviceMapper;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final ObjectProvider<ApnsClient> apnsClientProvider;
    private final ObjectMapper objectMapper;
    private final String apnsTopic;

    public NotificationService(PushDeviceMapper pushDeviceMapper,
                               ObjectProvider<FirebaseMessaging> firebaseMessagingProvider,
                               ObjectProvider<ApnsClient> apnsClientProvider,
                               ObjectMapper objectMapper,
                               @Value("${noos.mobile.push.apns.topic:}") String apnsTopic) {
        this.pushDeviceMapper = pushDeviceMapper;
        this.firebaseMessagingProvider = firebaseMessagingProvider;
        this.apnsClientProvider = apnsClientProvider;
        this.objectMapper = objectMapper;
        this.apnsTopic = apnsTopic;
    }

    public void notifySessionReady(String deviceId, Long userId, String sessionId, String planet) {
        notifySession(deviceId, userId, sessionId, planet, "session_ready");
    }

    public void notifySessionFailed(String deviceId, Long userId, String sessionId, String planet) {
        notifySession(deviceId, userId, sessionId, planet, "session_failed");
    }

    private void notifySession(String deviceId, Long userId, String sessionId, String planet, String type) {
        FirebaseMessaging fcm = firebaseMessagingProvider.getIfAvailable();
        ApnsClient apnsClient = apnsClientProvider.getIfAvailable();

        Map<String, String> payload = payload(type, sessionId, planet);
        for (PushDeviceRow target : pushDeviceMapper.findActive(deviceId, userId)) {
            if ("fcm".equals(target.getProvider())) {
                if (fcm == null) {
                    log.info("fcm disabled: would notify {} {}", type, sessionId);
                } else {
                    sendFcm(fcm, target, payload);
                }
            } else if ("apns".equals(target.getProvider())) {
                if (apnsClient == null) {
                    log.info("apns disabled: would notify {} {}", type, sessionId);
                } else {
                    sendApns(apnsClient, target, payload);
                }
            } else {
                log.warn("push skipped: unsupported provider {}", target.getProvider());
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
        } catch (FirebaseMessagingException e) {
            log.warn("push failed: deviceRegistrationId={}", target.getId(), e);
            if (isInactiveToken(e)) {
                pushDeviceMapper.deactivateById(target.getId());
            }
        } catch (Exception e) {
            log.warn("push failed: deviceRegistrationId={}", target.getId(), e);
        }
    }

    private boolean isInactiveToken(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private void sendApns(ApnsClient apnsClient, PushDeviceRow target, Map<String, String> payload) {
        try {
            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                    target.getToken(),
                    apnsTopic,
                    apnsPayload(payload)
            );
            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient
                    .sendNotification(notification)
                    .get(5, TimeUnit.SECONDS);
            if (!response.isAccepted()) {
                String reason = response.getRejectionReason().orElse("");
                log.warn("apns push rejected: deviceRegistrationId={} reason={}", target.getId(), reason);
                if (isInactiveApnsToken(reason)) {
                    pushDeviceMapper.deactivateById(target.getId());
                }
            }
        } catch (Exception e) {
            log.warn("apns push failed: deviceRegistrationId={}", target.getId(), e);
        }
    }

    private String apnsPayload(Map<String, String> payload) throws JsonProcessingException {
        Map<String, Object> root = Map.of(
                "aps", Map.of(
                        "alert", Map.of(
                                "title", payload.get("title"),
                                "body", payload.get("body")
                        )
                ),
                "type", payload.get("type"),
                "sessionId", payload.get("sessionId"),
                "planet", payload.getOrDefault("planet", ""),
                "title", payload.get("title"),
                "body", payload.get("body"),
                "deepLink", payload.get("deepLink")
        );
        return objectMapper.writeValueAsString(root);
    }

    private boolean isInactiveApnsToken(String reason) {
        return "Unregistered".equals(reason) || "BadDeviceToken".equals(reason);
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
