package com.bachatgat.repository;

import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Transitional Firestore-backed user repository.
 *
 * The legacy store remains the backing implementation during migration. This
 * class gives application services a user-specific contract so the backing
 * implementation can later be moved to direct Firestore queries without
 * changing authentication code.
 */
@Repository
public class FirestoreUserRepository implements UserRepository {
    private final FirestoreDataService legacyStore;

    public FirestoreUserRepository(FirestoreDataService legacyStore) {
        this.legacyStore = legacyStore;
    }

    @Override public Optional<User> findByUsername(String identifier) { return legacyStore.findUserByUsername(identifier); }
    @Override public List<User> findByIdentifier(String identifier) { return legacyStore.findUsersByIdentifier(identifier); }
    @Override public Optional<User> findById(String id) { return legacyStore.findUserById(id); }
    @Override public Optional<User> findByMemberId(String memberId) { return legacyStore.findUserByMemberId(memberId); }
    @Override public List<User> findByRole(Role role) { return legacyStore.findUsersByRole(role); }
    @Override public List<User> findAll() { return legacyStore.getAllUsers(); }
    @Override public User save(User user) { return legacyStore.saveUser(user); }
}
