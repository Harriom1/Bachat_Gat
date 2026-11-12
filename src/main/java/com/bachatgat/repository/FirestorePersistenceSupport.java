package com.bachatgat.repository;

import com.bachatgat.model.LoanRepaymentSchedule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Firestore serialization and durable-write operations.
 *
 * Domain repositories should use this class for the mechanics of converting
 * Java objects to Firestore documents. Business rules remain in services and
 * domain-specific repositories.
 */
public final class FirestorePersistenceSupport {

    private final Firestore firestore;
    private final ObjectMapper objectMapper;

    public FirestorePersistenceSupport(Firestore firestore, ObjectMapper objectMapper) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
    }

    public void persist(String collectionName, String documentId, Object object) {
        if (firestore == null) return;
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("Firestore document ID is required for " + collectionName);
        }
        try {
            Map<String, Object> firestoreData = objectMapper.convertValue(
                    object, new TypeReference<Map<String, Object>>() {});
            firestore.collection(collectionName).document(documentId).set(firestoreData).get();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not persist document '" + documentId + "' in Firestore collection '"
                            + collectionName + "'.", ex);
        }
    }

    public void persistLoanSchedule(String loanId, List<LoanRepaymentSchedule> schedule) {
        if (firestore == null) return;
        if (loanId == null || loanId.isBlank()) {
            throw new IllegalArgumentException("Loan ID is required for a repayment schedule");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loanId", loanId);
            payload.put("schedule", objectMapper.convertValue(schedule,
                    new TypeReference<List<Map<String, Object>>>() {}));
            firestore.collection("loanSchedules").document(loanId).set(payload).get();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not persist repayment schedule for loan '" + loanId + "'.", ex);
        }
    }
}
