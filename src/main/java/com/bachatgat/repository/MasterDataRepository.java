package com.bachatgat.repository;

import com.bachatgat.model.GroupMasterData;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Group rules/master-data persistence contract. */
public interface MasterDataRepository {
    Optional<GroupMasterData> findLatestByGroupId(String groupId);
    Optional<GroupMasterData> findForDate(String groupId, LocalDate date);
    List<GroupMasterData> findAllVersions(String groupId);
    GroupMasterData save(GroupMasterData masterData);
}
