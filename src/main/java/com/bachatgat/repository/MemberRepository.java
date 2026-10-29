package com.bachatgat.repository;

import com.bachatgat.model.Member;

import java.util.List;
import java.util.Optional;

/** Member-only persistence contract. */
public interface MemberRepository {
    List<Member> findByGroupId(String groupId);
    Optional<Member> findById(String id);
    Optional<Member> findByMemberId(String memberId);
    Member findByIdOrMemberId(String idOrMemberId);
    List<Member> findAll();
    Member save(Member member);
    boolean deleteById(String id);
    String generateNextRegistrationId(String groupId);
}
