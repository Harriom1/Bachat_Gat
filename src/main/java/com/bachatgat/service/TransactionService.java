package com.bachatgat.service;

import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final FirestoreDataService dataService;

    public TransactionService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public Transaction recordTransaction(String groupId, String memberId, String memberName, String loanId,
                                         TransactionType type, BigDecimal amount, BigDecimal principalAmount,
                                         BigDecimal interestAmount, LocalDate date, String reference,
                                         String description, String paymentMethod, String createdBy) {
        String txnId = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Transaction transaction = new Transaction(txnId, groupId, memberId, memberName, loanId, type,
                amount, principalAmount, interestAmount, date, reference, description, paymentMethod, createdBy);
        return dataService.saveTransaction(transaction);
    }

    public List<Transaction> getGroupTransactions(String groupId) {
        return dataService.getTransactionsByGroupId(groupId);
    }

    public List<Transaction> getMemberTransactions(String memberId) {
        return dataService.getTransactionsByMemberId(memberId);
    }

    public List<Transaction> getFilteredTransactions(String groupId, String memberId, Integer month, Integer year, String type) {
        List<Transaction> list;
        if (memberId != null && !memberId.isBlank()) {
            list = dataService.getTransactionsByMemberId(memberId);
        } else {
            list = dataService.getTransactionsByGroupId(groupId);
        }

        return list.stream()
                .filter(t -> {
                    if (groupId != null && !groupId.isBlank() && !groupId.equalsIgnoreCase(t.getGroupId())) {
                        return false;
                    }
                    if (memberId != null && !memberId.isBlank() && !memberId.equalsIgnoreCase(t.getMemberId())) {
                        return false;
                    }
                    if (month != null && month > 0 && t.getDate() != null && t.getDate().getMonthValue() != month) {
                        return false;
                    }
                    if (year != null && year > 0 && t.getDate() != null && t.getDate().getYear() != year) {
                        return false;
                    }
                    if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
                        if (t.getType() == null || !t.getType().name().equalsIgnoreCase(type)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }
}
