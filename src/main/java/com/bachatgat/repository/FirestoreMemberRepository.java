package com.bachatgat.repository;

import com.bachatgat.model.Member;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FirestoreMemberRepository implements MemberRepository {
    private final FirestoreDataService legacyStore;

    public FirestoreMemberRepository(FirestoreDataService legacyStore) {
        this.legacyStore = legacyStore;
    }

    @Override public List<Member> findByGroupId(String groupId) { return legacyStore.getMembersByGroupId(groupId); }
    @Override public Optional<Member> findById(String id) { return legacyStore.findMemberById(id); }
    @Override public Optional<Member> findByMemberId(String memberId) { return legacyStore.findMemberByMemberId(memberId); }
    @Override public Member findByIdOrMemberId(String idOrMemberId) { return legacyStore.getMemberById(idOrMemberId); }
    @Override public List<Member> findAll() { return legacyStore.getAllMembers(); }
    @Override public Member save(Member member) { return legacyStore.saveMember(member); }
    @Override public boolean deleteById(String id) { return legacyStore.deleteMember(id); }
    @Override public String generateNextRegistrationId(String groupId) { return legacyStore.generateNextMemberRegistrationId(groupId); }
}
