package com.commute.metrosync.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Configuration
 * Initializes Firebase Admin SDK on application startup
 */
@Startup
@ApplicationScoped
public class FirebaseConfig {

    private static final Logger LOG = Logger.getLogger(FirebaseConfig.class);

    @ConfigProperty(name = "firebase.credentials.path", defaultValue = "")
    String credentialsPath;

    @ConfigProperty(name = "firebase.credentials.json", defaultValue = "")
    String credentialsJson;

    @ConfigProperty(name = "firebase.project.id")
    String projectId;

    @ConfigProperty(name = "firebase.storage.bucket", defaultValue = "")
    String storageBucket;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = buildFirebaseOptions();
                FirebaseApp.initializeApp(options);
                LOG.info("Firebase initialized successfully for project: " + projectId);
            }
        } catch (IOException e) {
            LOG.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    private FirebaseOptions buildFirebaseOptions() throws IOException {
        GoogleCredentials credentials = getCredentials();
        
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId);

        if (storageBucket != null && !storageBucket.isEmpty()) {
            builder.setStorageBucket(storageBucket);
        }

        return builder.build();
    }

    private GoogleCredentials getCredentials() throws IOException {
        // Option 1: Load from file path
        if (credentialsPath != null && !credentialsPath.isEmpty()) {
            LOG.info("Loading Firebase credentials from file: " + credentialsPath);
            try (InputStream serviceAccount = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(serviceAccount);
            }
        }
        
        // Option 2: Load from JSON string (environment variable)
        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            LOG.info("Loading Firebase credentials from JSON string");
            try (InputStream serviceAccount = new ByteArrayInputStream(
                    credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(serviceAccount);
            }
        }
        
        // Option 3: Application Default Credentials (for Google Cloud)
        LOG.info("Loading Firebase Application Default Credentials");
        return GoogleCredentials.getApplicationDefault();
    }

    public String getProjectId() {
        return projectId;
    }

    public String getStorageBucket() {
        return storageBucket;
    }
}