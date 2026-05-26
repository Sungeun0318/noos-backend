package com.noos.backend.mobile.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    @ConditionalOnProperty(name = "noos.mobile.push.fcm.credentials-path")
    @Conditional(FcmCredentialsFileCondition.class)
    public FirebaseApp firebaseApp(@Value("${noos.mobile.push.fcm.credentials-path}") String path) throws Exception {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if ("noos-mobile".equals(app.getName())) {
                return app;
            }
        }

        try (FileInputStream creds = new FileInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(creds))
                    .build();
            return FirebaseApp.initializeApp(options, "noos-mobile");
        }
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    static class FcmCredentialsFileCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String path = context.getEnvironment().getProperty("noos.mobile.push.fcm.credentials-path");
            boolean enabled = path != null && !path.isBlank() && Files.isRegularFile(Path.of(path));
            if (!enabled) {
                log.warn("FCM credentials not found at {} - push disabled", path);
            }
            return enabled;
        }
    }
}
