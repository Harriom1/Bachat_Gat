package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Member {
    @DocumentId
    private String id;
    private String memberId;         // e.g. MPBG-M001
    private String registrationId;   // e.g. MEM-2026-000001
    private String groupId;
    private String groupName;
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;
    private String guardianName;     // Father / Husband's Name
    private String gender = "FEMALE";
    private LocalDate dob;
    private String mobileNumber;
    private String alternateMobile;
    private String email;
    private String address;
    private String village;
    private String taluka;
    private String district;
    private String state = "Maharashtra";
    private String pinCode;
    private String occupation;
    private LocalDate joiningDate = LocalDate.now();
    private MemberStatus status = MemberStatus.ACTIVE;
    private String designation = "MEMBER"; // PRESIDENT, SECRETARY, TREASURER, MEMBER
    private boolean hasLoginAccount = false;
    private String loginUsername;

    // Financial & Bachat Details
    /** Member-level legacy value retained only to read old records; group Monthly Bachat is now the source of truth. */
    private BigDecimal monthlyBachatAmount = BigDecimal.valueOf(5000);
    private BigDecimal initialShareAmount = BigDecimal.ZERO;
    private BigDecimal currentShareBalance = BigDecimal.ZERO;
    private BigDecimal membershipFee = BigDecimal.valueOf(200);
    private BigDecimal totalSavingsBalance = BigDecimal.ZERO;
    private BigDecimal pendingSavings = BigDecimal.ZERO;
    private BigDecimal totalSavingsTarget = BigDecimal.valueOf(60000); // 12 months @ 5000
    private LocalDate bachatStartDate = LocalDate.now();
    private LocalDate bachatEndDate = LocalDate.now().plusMonths(12);
    private LocalDate nextDueDate = LocalDate.now().withDayOfMonth(10);

    // Identity (Secured / Masked where required)
    private String panNumber;
    private String panDocumentPath;
    private String aadhaarReference;
    private String otherIdType;
    private String otherIdReference;

    // Nominee Information
    private String nomineeName;
    private String nomineeRelation;
    private String nomineeMobile;
    private String nomineeAddress;

    // Bank Information
    private String bankName;
    private String accountNumber;
    private String ifsc;
    private String branch;

    // Active Loan Information
    private String activeLoanId;
    private BigDecimal currentLoanOutstanding = BigDecimal.ZERO;
    private BigDecimal totalLoanInterestPaid = BigDecimal.ZERO;

    // Loan Interest Distributed to Member as Group Income Dividend
    private BigDecimal totalLoanInterestReceived = BigDecimal.ZERO;
    private BigDecimal pendingLateFees = BigDecimal.ZERO;
    private BigDecimal totalPenaltiesPaid = BigDecimal.ZERO;

    // Other Income & Expense Distribution Totals
    private BigDecimal totalOtherIncomeDistributed = BigDecimal.ZERO;
    private BigDecimal totalOtherExpenseDistributed = BigDecimal.ZERO;

    // Metadata
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Member() {}

    public BigDecimal getMyIncome() {
        BigDecimal loanInt = totalLoanInterestReceived != null ? totalLoanInterestReceived : BigDecimal.ZERO;
        BigDecimal otherInc = totalOtherIncomeDistributed != null ? totalOtherIncomeDistributed : BigDecimal.ZERO;
        return loanInt.add(otherInc);
    }

    public BigDecimal getNetMemberBalance() {
        BigDecimal savings = totalSavingsBalance != null ? totalSavingsBalance : BigDecimal.ZERO;
        BigDecimal income = getMyIncome();
        BigDecimal loanOut = currentLoanOutstanding != null ? currentLoanOutstanding : BigDecimal.ZERO;
        BigDecimal penalties = pendingLateFees != null ? pendingLateFees : BigDecimal.ZERO;
        return savings.add(income).subtract(loanOut).subtract(penalties);
    }

    public BigDecimal getTotalFinancialPosition() {
        return getNetMemberBalance();
    }


    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public boolean isHasLoginAccount() { return hasLoginAccount; }
    public boolean hasLoginAccount() { return hasLoginAccount; }
    public void setHasLoginAccount(boolean hasLoginAccount) { this.hasLoginAccount = hasLoginAccount; }
    public String getLoginUsername() { return loginUsername; }
    public void setLoginUsername(String loginUsername) { this.loginUsername = loginUsername; }
    public String getGuardianName() { return guardianName; }
    public void setGuardianName(String guardianName) { this.guardianName = guardianName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getAlternateMobile() { return alternateMobile; }
    public void setAlternateMobile(String alternateMobile) { this.alternateMobile = alternateMobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }
    public String getTaluka() { return taluka; }
    public void setTaluka(String taluka) { this.taluka = taluka; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
    public MemberStatus getStatus() { return status; }
    public void setStatus(MemberStatus status) { this.status = status; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    @JsonIgnore
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    @JsonIgnore
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getInitialShareAmount() { return initialShareAmount; }
    public void setInitialShareAmount(BigDecimal initialShareAmount) { this.initialShareAmount = initialShareAmount; }
    public BigDecimal getCurrentShareBalance() { return currentShareBalance; }
    public void setCurrentShareBalance(BigDecimal currentShareBalance) { this.currentShareBalance = currentShareBalance; }
    public BigDecimal getMembershipFee() { return membershipFee; }
    public void setMembershipFee(BigDecimal membershipFee) { this.membershipFee = membershipFee; }
    public BigDecimal getTotalSavingsBalance() { return totalSavingsBalance; }
    public void setTotalSavingsBalance(BigDecimal totalSavingsBalance) { this.totalSavingsBalance = totalSavingsBalance; }
    public BigDecimal getTotalSavings() { return totalSavingsBalance; }
    public void setTotalSavings(BigDecimal totalSavings) { this.totalSavingsBalance = totalSavings; }
    public BigDecimal getPendingSavings() { return pendingSavings; }
    public void setPendingSavings(BigDecimal pendingSavings) { this.pendingSavings = pendingSavings; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
    public String getPanDocumentPath() { return panDocumentPath; }
    public void setPanDocumentPath(String panDocumentPath) { this.panDocumentPath = panDocumentPath; }
    public String getAadhaarReference() { return aadhaarReference; }
    public void setAadhaarReference(String aadhaarReference) { this.aadhaarReference = aadhaarReference; }
    public String getOtherIdType() { return otherIdType; }
    public void setOtherIdType(String otherIdType) { this.otherIdType = otherIdType; }
    public String getOtherIdReference() { return otherIdReference; }
    public void setOtherIdReference(String otherIdReference) { this.otherIdReference = otherIdReference; }
    public String getNomineeName() { return nomineeName; }
    public void setNomineeName(String nomineeName) { this.nomineeName = nomineeName; }
    public String getNomineeRelation() { return nomineeRelation; }
    public void setNomineeRelation(String nomineeRelation) { this.nomineeRelation = nomineeRelation; }
    public String getNomineeMobile() { return nomineeMobile; }
    public void setNomineeMobile(String nomineeMobile) { this.nomineeMobile = nomineeMobile; }
    public String getNomineeAddress() { return nomineeAddress; }
    public void setNomineeAddress(String nomineeAddress) { this.nomineeAddress = nomineeAddress; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getActiveLoanId() { return activeLoanId; }
    public void setActiveLoanId(String activeLoanId) { this.activeLoanId = activeLoanId; }
    public BigDecimal getCurrentLoanOutstanding() { return currentLoanOutstanding; }
    public void setCurrentLoanOutstanding(BigDecimal currentLoanOutstanding) { this.currentLoanOutstanding = currentLoanOutstanding; }
    public BigDecimal getTotalLoanInterestPaid() { return totalLoanInterestPaid; }
    public void setTotalLoanInterestPaid(BigDecimal totalLoanInterestPaid) { this.totalLoanInterestPaid = totalLoanInterestPaid; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    @JsonIgnore
    public BigDecimal getMonthlyCommittedAmount() { return monthlyBachatAmount; }
    @JsonIgnore
    public void setMonthlyCommittedAmount(BigDecimal monthlyCommittedAmount) { this.monthlyBachatAmount = monthlyCommittedAmount; }
    public BigDecimal getTotalSavingsTarget() { return totalSavingsTarget; }
    public void setTotalSavingsTarget(BigDecimal totalSavingsTarget) { this.totalSavingsTarget = totalSavingsTarget; }
    public LocalDate getBachatStartDate() { return bachatStartDate != null ? bachatStartDate : joiningDate; }
    public void setBachatStartDate(LocalDate bachatStartDate) { this.bachatStartDate = bachatStartDate; }
    public LocalDate getBachatEndDate() { return bachatEndDate; }
    public void setBachatEndDate(LocalDate bachatEndDate) { this.bachatEndDate = bachatEndDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public BigDecimal getTotalOtherIncomeDistributed() { return totalOtherIncomeDistributed != null ? totalOtherIncomeDistributed : BigDecimal.ZERO; }
    public void setTotalOtherIncomeDistributed(BigDecimal totalOtherIncomeDistributed) { this.totalOtherIncomeDistributed = totalOtherIncomeDistributed; }
    public BigDecimal getTotalOtherExpenseDistributed() { return totalOtherExpenseDistributed != null ? totalOtherExpenseDistributed : BigDecimal.ZERO; }
    public void setTotalOtherExpenseDistributed(BigDecimal totalOtherExpenseDistributed) { this.totalOtherExpenseDistributed = totalOtherExpenseDistributed; }
    public BigDecimal getTotalLoanInterestReceived() { return totalLoanInterestReceived != null ? totalLoanInterestReceived : BigDecimal.ZERO; }
    public void setTotalLoanInterestReceived(BigDecimal totalLoanInterestReceived) { this.totalLoanInterestReceived = totalLoanInterestReceived; }
    public BigDecimal getPendingLateFees() { return pendingLateFees != null ? pendingLateFees : BigDecimal.ZERO; }
    public void setPendingLateFees(BigDecimal pendingLateFees) { this.pendingLateFees = pendingLateFees; }
    public BigDecimal getTotalPenaltiesPaid() { return totalPenaltiesPaid != null ? totalPenaltiesPaid : BigDecimal.ZERO; }
    public void setTotalPenaltiesPaid(BigDecimal totalPenaltiesPaid) { this.totalPenaltiesPaid = totalPenaltiesPaid; }
    public String getDesignation() { return designation != null ? designation : "MEMBER"; }
    public void setDesignation(String designation) { this.designation = designation != null ? designation : "MEMBER"; }
}

