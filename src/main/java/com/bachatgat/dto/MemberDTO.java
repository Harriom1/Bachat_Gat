package com.bachatgat.dto;

import com.bachatgat.model.MemberStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

public class MemberDTO {
    private String id;
    private String memberId;
    
    @NotBlank(message = "Group ID is mandatory")
    private String groupId;
    private String groupName;
    private String registrationId; // Auto-generated if blank e.g. MEM-2026-000001
    
    @NotBlank(message = "Member Full Name is mandatory")
    private String fullName;
    private String fullNameMr;
    private String fullNameHi;
    private String designation = "MEMBER"; // PRESIDENT, SECRETARY, TREASURER, MEMBER
    
    private boolean createLoginAccount = false;
    private String loginUsername;
    private String temporaryPassword;
    private String password;
    private String confirmPassword;
    
    @NotBlank(message = "Father / Husband's Name is mandatory")
    private String guardianName;
    
    private String gender = "FEMALE";
    
    @NotNull(message = "Date of Birth is mandatory")
    private LocalDate dob;
    
    @NotBlank(message = "Mobile Number is mandatory")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Valid 10-digit Indian mobile number is mandatory (starting with 6, 7, 8, or 9)")
    private String mobileNumber;
    
    private String alternateMobile;
    private String email;
    
    @NotBlank(message = "Residential Address is mandatory")
    private String address;
    
    @NotBlank(message = "Village / Town is mandatory")
    private String village;
    
    @NotBlank(message = "Taluka is mandatory")
    private String taluka;
    
    @NotBlank(message = "District is mandatory")
    private String district;
    
    private String state = "Maharashtra";
    
    @NotBlank(message = "PIN code is mandatory")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Valid 6-digit Indian PIN code is mandatory")
    private String pinCode;
    
    private String occupation;
    private LocalDate joiningDate;
    private MemberStatus status;

    @NotNull(message = "Monthly Bachat is mandatory")
    @Positive(message = "Monthly Bachat must be greater than zero")
    @JsonAlias("monthlyShareAmount")
    private BigDecimal monthlyBachatAmount;
    private BigDecimal initialShareAmount;
    private BigDecimal currentShareBalance;
    private BigDecimal membershipFee;
    private BigDecimal totalSavingsBalance;
    private BigDecimal pendingSavings;
    private BigDecimal totalSavingsTarget;
    
    private LocalDate bachatStartDate;
    private LocalDate bachatEndDate;
    private LocalDate nextDueDate;

    @NotBlank(message = "PAN Number is mandatory")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Valid PAN format is mandatory (e.g. ABCDE1234F)")
    private String panNumber;
    
    private String panDocumentPath;
    
    @NotBlank(message = "Aadhaar / ID reference is mandatory")
    private String aadhaarReference;
    
    private String otherIdType;
    private String otherIdReference;

    @NotBlank(message = "Nominee Name is mandatory")
    private String nomineeName;
    
    @NotBlank(message = "Nominee Relationship is mandatory")
    private String nomineeRelation;
    
    @NotBlank(message = "Nominee Contact Number is mandatory")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Valid 10-digit Nominee mobile is mandatory")
    private String nomineeMobile;
    
    private String nomineeAddress;

    @NotBlank(message = "Bank Name is mandatory")
    private String bankName;
    
    @NotBlank(message = "Bank Account Number is mandatory")
    @Pattern(regexp = "^[0-9]{9,18}$", message = "Bank account number must be between 9 and 18 digits")
    private String accountNumber;
    
    @NotBlank(message = "IFSC code is mandatory")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Valid IFSC Code format is mandatory (e.g. MAHB0000452)")
    private String ifsc;
    
    private String branch;

    private String activeLoanId;
    private BigDecimal currentLoanOutstanding;
    private BigDecimal totalLoanInterestPaid;

    public MemberDTO() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFullNameMr() { return fullNameMr; }
    public void setFullNameMr(String fullNameMr) { this.fullNameMr = fullNameMr; }
    public String getFullNameHi() { return fullNameHi; }
    public void setFullNameHi(String fullNameHi) { this.fullNameHi = fullNameHi; }
    public boolean isCreateLoginAccount() { return createLoginAccount; }
    public void setCreateLoginAccount(boolean createLoginAccount) { this.createLoginAccount = createLoginAccount; }
    public String getLoginUsername() { return loginUsername; }
    public void setLoginUsername(String loginUsername) { this.loginUsername = loginUsername; }
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
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
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getMonthlyCommittedAmount() { return monthlyBachatAmount; }
    public void setMonthlyCommittedAmount(BigDecimal monthlyCommittedAmount) { this.monthlyBachatAmount = monthlyCommittedAmount; }
    public BigDecimal getInitialShareAmount() { return initialShareAmount; }
    public void setInitialShareAmount(BigDecimal initialShareAmount) { this.initialShareAmount = initialShareAmount; }
    public BigDecimal getCurrentShareBalance() { return currentShareBalance; }
    public void setCurrentShareBalance(BigDecimal currentShareBalance) { this.currentShareBalance = currentShareBalance; }
    public BigDecimal getMembershipFee() { return membershipFee; }
    public void setMembershipFee(BigDecimal membershipFee) { this.membershipFee = membershipFee; }
    public BigDecimal getTotalSavingsBalance() { return totalSavingsBalance; }
    public void setTotalSavingsBalance(BigDecimal totalSavingsBalance) { this.totalSavingsBalance = totalSavingsBalance; }
    public BigDecimal getPendingSavings() { return pendingSavings; }
    public void setPendingSavings(BigDecimal pendingSavings) { this.pendingSavings = pendingSavings; }
    public BigDecimal getTotalSavingsTarget() { return totalSavingsTarget; }
    public void setTotalSavingsTarget(BigDecimal totalSavingsTarget) { this.totalSavingsTarget = totalSavingsTarget; }
    public LocalDate getBachatStartDate() { return bachatStartDate; }
    public void setBachatStartDate(LocalDate bachatStartDate) { this.bachatStartDate = bachatStartDate; }
    public LocalDate getBachatEndDate() { return bachatEndDate; }
    public void setBachatEndDate(LocalDate bachatEndDate) { this.bachatEndDate = bachatEndDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
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
    public String getDesignation() { return designation != null ? designation : "MEMBER"; }
    public void setDesignation(String designation) { this.designation = designation != null ? designation : "MEMBER"; }
}
