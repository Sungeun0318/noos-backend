package com.noos.backend.mobile.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(pushDeviceMapper, provider(firebaseMessaging));
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

    private PushDeviceRow fcmTarget(String id) {
        PushDeviceRow row = new PushDeviceRow();
        row.setId(id);
        row.setProvider("fcm");
        row.setToken("token_" + id);
        return row;
    }

    private FirebaseMessagingException messagingException(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        return exception;
    }

    private ObjectProvider<FirebaseMessaging> provider(FirebaseMessaging messaging) {
        return new ObjectProvider<>() {
            @Override
            public FirebaseMessaging getObject(Object... args) {
                return messaging;
            }

            @Override
            public FirebaseMessaging getIfAvailable() {
                return messaging;
            }

            @Override
            public FirebaseMessaging getObject() {
                return messaging;
            }

            @Override
            public Iterator<FirebaseMessaging> iterator() {
                return List.of(messaging).iterator();
            }

            @Override
            public Stream<FirebaseMessaging> stream() {
                return Stream.of(messaging);
            }

            @Override
            public Stream<FirebaseMessaging> orderedStream() {
                return Stream.of(messaging);
            }
        };
    }
}
