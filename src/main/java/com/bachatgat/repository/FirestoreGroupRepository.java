package com.bachatgat.repository;

import com.bachatgat.model.Group;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FirestoreGroupRepository implements GroupRepository {
    private final FirestoreDataService legacyStore;

    public FirestoreGroupRepository(FirestoreDataService legacyStore) {
        this.legacyStore = legacyStore;
    }

    @Override public List<Group> findAll() { return legacyStore.getAllGroups(); }
    @Override public Optional<Group> findById(String id) { return legacyStore.findGroupById(id); }
    @Override public Optional<Group> findByRegistrationId(String registrationId) { return legacyStore.findGroupByRegistrationId(registrationId); }
    @Override public Optional<Group> findByGroupCode(String groupCode) { return legacyStore.findGroupByGroupCode(groupCode); }
    @Override public Group save(Group group) { return legacyStore.saveGroup(group); }
    @Override public String generateNextGroupCode() { return legacyStore.generateNextGroupCode(); }
    @Override public String generateNextRegistrationId() { return legacyStore.generateNextGroupRegistrationId(); }
}
