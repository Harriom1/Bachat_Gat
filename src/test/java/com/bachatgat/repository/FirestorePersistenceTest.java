package com.bachatgat.repository;

import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a record survives replacement of the repository instance.
 * This test is enabled only when FIRESTORE_EMULATOR_HOST is explicitly set.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestorePersistenceTest {

    @Autowired
    private Firestore firestore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    void savedUserSurvivesRepositoryRestart() throws Exception {
        String id = "test-restart-" + UUID.randomUUID();
        User saved = new User("restart-user-" + UUID.randomUUID(),
                passwordEncoder.encode("test-password"),
                "restart@example.test", "Restart Test User", Role.MEMBER, "test-group", "test-member");
        saved.setId(id);

        dataService.saveUser(saved);

        FirestoreDataService restarted = new FirestoreDataService(
                firestore, passwordEncoder, objectMapper, false);
        restarted.init();

        assertTrue(restarted.findUserById(id).isPresent());
        assertEquals(saved.getUsername(), restarted.findUserById(id).orElseThrow().getUsername());

        firestore.collection("users").document(id).delete().get();
    }
}
