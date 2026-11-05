package com.bachatgat.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides the Firebase client configuration without storing it in public source files.
 * These values are client configuration, not server credentials. The API key must still
 * be restricted in Google Cloud by authorized websites and required APIs.
 */
@RestController
@RequestMapping("/api/config")
public class FirebaseWebConfigController {
    private final String apiKey;
    private final String authDomain;
    private final String projectId;
    private final String storageBucket;
    private final String messagingSenderId;
    private final String appId;

    public FirebaseWebConfigController(
            @Value("${firebase.web.api-key:}") String apiKey,
            @Value("${firebase.web.auth-domain}") String authDomain,
            @Value("${firebase.web.project-id}") String projectId,
            @Value("${firebase.web.storage-bucket}") String storageBucket,
            @Value("${firebase.web.messaging-sender-id}") String messagingSenderId,
            @Value("${firebase.web.app-id}") String appId) {
        this.apiKey = apiKey;
        this.authDomain = authDomain;
        this.projectId = projectId;
        this.storageBucket = storageBucket;
        this.messagingSenderId = messagingSenderId;
        this.appId = appId;
    }

    @GetMapping("/firebase")
    public ResponseEntity<Map<String, String>> firebaseConfig() {
        if (apiKey.isBlank()) {
            return ResponseEntity.internalServerError().build();
        }
        Map<String, String> config = new LinkedHashMap<>();
        config.put("apiKey", apiKey);
        config.put("authDomain", authDomain);
        config.put("projectId", projectId);
        config.put("storageBucket", storageBucket);
        config.put("messagingSenderId", messagingSenderId);
        config.put("appId", appId);
        return ResponseEntity.ok(config);
    }
}
