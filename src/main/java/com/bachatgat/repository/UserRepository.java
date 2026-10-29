package com.bachatgat.repository;

import com.bachatgat.model.Role;
import com.bachatgat.model.User;

import java.util.List;
import java.util.Optional;

/** User-only persistence contract. */
public interface UserRepository {
    Optional<User> findByUsername(String identifier);
    List<User> findByIdentifier(String identifier);
    Optional<User> findById(String id);
    Optional<User> findByMemberId(String memberId);
    List<User> findByRole(Role role);
    List<User> findAll();
    User save(User user);
}
