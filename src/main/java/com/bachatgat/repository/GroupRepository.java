package com.bachatgat.repository;

import com.bachatgat.model.Group;

import java.util.List;
import java.util.Optional;

/** Group-only persistence contract. */
public interface GroupRepository {
    List<Group> findAll();
    Optional<Group> findById(String id);
    Optional<Group> findByRegistrationId(String registrationId);
    Optional<Group> findByGroupCode(String groupCode);
    Group save(Group group);
    String generateNextGroupCode();
    String generateNextRegistrationId();
}
