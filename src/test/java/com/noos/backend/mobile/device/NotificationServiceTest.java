package com.noos.backend.mobile.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.noos.backend.mobile.device.dto.PushDeviceRow;
import com.noos.backend.mobile.device.mapper.PushDeviceMapper;
import com.noos.backend.mobile.device.service.NotificationService;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private PushDeviceMapper pushDeviceMapper;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private ApnsClient apnsClient;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = service(firebaseMessaging, null);
    }

    @Test
    void unregisteredFcmTokenDeactivatesPushDevice() throws Exception {
        PushDeviceRow target = fcmTarget("dreg_unregistered");
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.UNREGISTERED);
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(target));
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper).deactivateById("dreg_unregistered");
    }

    @Test
    void invalidArgumentFcmTokenDeactivatesPushDevice() throws Exception {
        PushDeviceRow target = fcmTarget("dreg_invalid");
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.INVALID_ARGUMENT);
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(target));
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper).deactivateById("dreg_invalid");
    }

    @Test
    void transientFcmErrorDoesNotDeactivatePushDevice() throws Exception {
        PushDeviceRow target = fcmTarget("dreg_transient");
        FirebaseMessagingException exception = messagingException(MessagingErrorCode.UNAVAILABLE);
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(target));
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper, never()).deactivateById("dreg_transient");
    }

    @Test
    void apnsTokenNoOpsWhenApnsClientIsMissing() {
        notificationService = service(null, null);
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(apnsTarget("dreg_apns_no_client")));

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper, never()).deactivateById("dreg_apns_no_client");
    }

    @Test
    void acceptedApnsResponseDoesNotDeactivatePushDevice() {
        notificationService = service(null, apnsClient);
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                apnsFuture(true, null);
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(apnsTarget("dreg_apns_ok")));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper, never()).deactivateById("dreg_apns_ok");
    }

    @Test
    void unregisteredApnsTokenDeactivatesPushDevice() {
        notificationService = service(null, apnsClient);
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                apnsFuture(false, "Unregistered");
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(apnsTarget("dreg_apns_unregistered")));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper).deactivateById("dreg_apns_unregistered");
    }

    @Test
    void badDeviceTokenApnsTokenDeactivatesPushDevice() {
        notificationService = service(null, apnsClient);
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                apnsFuture(false, "BadDeviceToken");
        when(pushDeviceMapper.findActive("dev_push", null)).thenReturn(List.of(apnsTarget("dreg_apns_bad_token")));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(pushDeviceMapper).deactivateById("dreg_apns_bad_token");
    }

    @Test
    void mixedFcmAndApnsTargetsSendThroughEachProvider() throws Exception {
        notificationService = service(firebaseMessaging, apnsClient);
        when(pushDeviceMapper.findActive("dev_push", null))
                .thenReturn(List.of(fcmTarget("dreg_fcm"), apnsTarget("dreg_apns")));
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                apnsFuture(true, null);
        when(firebaseMessaging.send(any(Message.class))).thenReturn("fcm-message-id");
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(future);

        notificationService.notifySessionReady("dev_push", null, "session_push", "Mars");

        verify(firebaseMessaging).send(any(Message.class));
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
    }

    private PushDeviceRow fcmTarget(String id) {
        PushDeviceRow row = new PushDeviceRow();
        row.setId(id);
        row.setProvider("fcm");
        row.setToken("token_" + id);
        return row;
    }

    private PushDeviceRow apnsTarget(String id) {
        PushDeviceRow row = new PushDeviceRow();
        row.setId(id);
        row.setProvider("apns");
        row.setToken("token_" + id);
        return row;
    }

    private FirebaseMessagingException messagingException(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        return exception;
    }

    @SuppressWarnings("unchecked")
    private PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> apnsFuture(
            boolean accepted,
            String rejectionReason
    ) {
        PushNotificationResponse<SimpleApnsPushNotification> response = mock(PushNotificationResponse.class);
        when(response.isAccepted()).thenReturn(accepted);
        if (!accepted) {
            when(response.getRejectionReason()).thenReturn(java.util.Optional.of(rejectionReason));
        }
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
                new PushNotificationFuture<>(new SimpleApnsPushNotification("token", "ai.noos.mobile", "{}"));
        future.complete(response);
        return future;
    }

    private NotificationService service(FirebaseMessaging fcm, ApnsClient apns) {
        return new NotificationService(
                pushDeviceMapper,
                provider(fcm),
                provider(apns),
                new ObjectMapper(),
                "ai.noos.mobile"
        );
    }

    private <T> ObjectProvider<T> provider(T object) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return object;
            }

            @Override
            public T getIfAvailable() {
                return object;
            }

            @Override
            public T getObject() {
                return object;
            }

            @Override
            public Iterator<T> iterator() {
                return object == null ? List.<T>of().iterator() : List.of(object).iterator();
            }

            @Override
            public Stream<T> stream() {
                return object == null ? Stream.empty() : Stream.of(object);
            }

            @Override
            public Stream<T> orderedStream() {
                return object == null ? Stream.empty() : Stream.of(object);
            }
        };
    }
}
