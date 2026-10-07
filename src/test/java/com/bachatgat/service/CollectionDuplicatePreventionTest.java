package com.bachatgat.service;

import com.bachatgat.model.CollectionRecord;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CollectionDuplicatePreventionTest {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    @DisplayName("Generating monthly collections multiple times is idempotent and never creates duplicate records")
    void testMonthlyCollectionDuplicatePrevention() {
        String groupId = "bg-001";
        int testMonth = 11;
        int testYear = 2026;

        // 1st Run: Should create records for active members
        List<CollectionRecord> run1 = collectionService.generateMonthlyCollections(groupId, testMonth, testYear, "admin");
        assertNotNull(run1);
        assertEquals(20, run1.size());

        // 2nd Run: Should generate 0 additional records because business keys already exist
        List<CollectionRecord> run2 = collectionService.generateMonthlyCollections(groupId, testMonth, testYear, "admin");
        assertNotNull(run2);
        assertEquals(0, run2.size(), "Idempotent scheduler should not create duplicate records");
    }
}
