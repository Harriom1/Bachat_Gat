package com.bachatgat.repository;

import com.bachatgat.model.GroupMasterData;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class FirestoreMasterDataRepository implements MasterDataRepository {
    private final FirestoreDataService legacyStore;

    public FirestoreMasterDataRepository(FirestoreDataService legacyStore) {
        this.legacyStore = legacyStore;
    }

    @Override public Optional<GroupMasterData> findLatestByGroupId(String groupId) { return legacyStore.getLatestMasterDataByGroupId(groupId); }
    @Override public Optional<GroupMasterData> findForDate(String groupId, LocalDate date) { return legacyStore.getMasterDataForDate(groupId, date); }
    @Override public List<GroupMasterData> findAllVersions(String groupId) { return legacyStore.getAllMasterDataVersions(groupId); }
    @Override public GroupMasterData save(GroupMasterData masterData) { return legacyStore.saveMasterData(masterData); }
}
