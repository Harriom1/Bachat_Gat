package com.bachatgat.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirestoreConfig {

    private static final Logger log = LoggerFactory.getLogger(FirestoreConfig.class);

    @Value("${gcp.project-id:midc-doc-uploader-revamp}")
    private String projectId;

    @Value("${gcp.firestore.database:(default)}")
    private String databaseId;

    @Value("${gcp.firestore.emulator.enabled:false}")
    private boolean emulatorEnabled;

    @Value("${gcp.firestore.emulator.host:127.0.0.1:8081}")
    private String emulatorHost;

    @Bean
    public Firestore firestore() {
        try {
            log.info("Initializing Google Cloud Firestore connection for project: {} and database: {}", projectId, databaseId);
            FirestoreOptions.Builder optionsBuilder = FirestoreOptions.newBuilder()
                    .setProjectId(projectId);

            if (emulatorEnabled) {
                optionsBuilder.setEmulatorHost(emulatorHost);
                log.info("Using Firestore emulator at {}.", emulatorHost);
            }

            if (databaseId != null && !databaseId.isEmpty() && !databaseId.equals("(default)")) {
                optionsBuilder.setDatabaseId(databaseId);
            }

            Firestore firestore = optionsBuilder.build().getService();
            log.info("Google Cloud Firestore client successfully initialized via Application Default Credentials (ADC).");
            return firestore;
        } catch (Exception ex) {
            log.warn("Could not connect directly to Google Cloud Firestore via ADC ({}). Falling back to resilient standalone/emulator mode.", ex.getMessage());
            return null;
        }
    }
}
