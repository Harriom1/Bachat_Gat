package com.bachatgat.service;

import com.bachatgat.dto.GroupDTO;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.Group;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupService {

    private final FirestoreDataService dataService;
    private final AuditService auditService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public GroupService(FirestoreDataService dataService, AuditService auditService,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.dataService = dataService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Group> getAllGroups() {
        return dataService.getAllGroups();
    }

    public Group getGroupById(String id) {
        return dataService.findGroupById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + id));
    }

    public Group createGroup(GroupDTO dto, String createdBy) {
        // Auto-generate Group Code if not provided
        if (dto.getGroupCode() == null || dto.getGroupCode().isBlank()) {
            dto.setGroupCode(dataService.generateNextGroupCode());
        } else {
            dataService.findGroupByGroupCode(dto.getGroupCode())
                    .ifPresent(g -> {
                        throw new DuplicateRecordException("Group with Code '" + dto.getGroupCode() + "' already exists");
                    });
        }

        // Auto-generate Registration ID if not provided
        if (dto.getRegistrationId() == null || dto.getRegistrationId().isBlank()) {
            dto.setRegistrationId(dataService.generateNextGroupRegistrationId());
        } else {
            dataService.findGroupByRegistrationId(dto.getRegistrationId())
                    .ifPresent(g -> {
                        throw new DuplicateRecordException("Group with Registration ID '" + dto.getRegistrationId() + "' already exists");
                    });
        }

        Group group = new Group();
        mapDtoToGroup(dto, group);
        validateLoanLimits(group);
        group.setCreatedBy(createdBy);

        Group saved = dataService.saveGroup(group);

        // Mandatory President Account Provisioning (Requirements 7, 8 & 10)
        String presidentPassword = (dto.getPresidentPassword() != null && !dto.getPresidentPassword().isBlank())
                ? dto.getPresidentPassword().trim()
                : (dto.getTemporaryAdminPassword() != null ? dto.getTemporaryAdminPassword().trim() : null);

        if (presidentPassword == null || presidentPassword.isBlank()) {
            throw new com.bachatgat.exception.InvalidFinancialOperationException("President password is required during group creation");
        }
        if (dto.getConfirmPresidentPassword() != null && !dto.getConfirmPresidentPassword().isBlank()
                && !presidentPassword.equals(dto.getConfirmPresidentPassword().trim())) {
            throw new com.bachatgat.exception.InvalidFinancialOperationException("President password and confirmation password do not match");
        }
        if (presidentPassword.length() < 6) {
            throw new com.bachatgat.exception.InvalidFinancialOperationException("President password must be at least 6 characters");
        }

        if (passwordEncoder != null) {

            String presidentEmail = (dto.getPresidentEmail() != null && !dto.getPresidentEmail().isBlank())
                    ? dto.getPresidentEmail().trim()
                    : ((dto.getEmail() != null && !dto.getEmail().isBlank()) ? dto.getEmail().trim() : null);
            String presidentMobile = (dto.getPresidentMobile() != null && !dto.getPresidentMobile().isBlank())
                    ? dto.getPresidentMobile().trim()
                    : ((dto.getContactNumber() != null && !dto.getContactNumber().isBlank()) ? dto.getContactNumber().trim() : null);

            String presidentUserLogin = (dto.getPresidentUsername() != null && !dto.getPresidentUsername().isBlank())
                    ? dto.getPresidentUsername().trim()
                    : (presidentEmail != null
                        ? presidentEmail
                        : (presidentMobile != null
                            ? presidentMobile
                            : "admin_" + saved.getGroupCode().toLowerCase().replace("-", "")));

            com.bachatgat.model.User newAdmin;
            var existingUser = dataService.findUserByUsername(presidentUserLogin)
                    .or(() -> presidentEmail != null ? dataService.findUserByUsername(presidentEmail) : java.util.Optional.empty())
                    .or(() -> presidentMobile != null ? dataService.findUserByUsername(presidentMobile) : java.util.Optional.empty());

            if (existingUser.isPresent()) {
                newAdmin = existingUser.get();
                newAdmin.setPassword(passwordEncoder.encode(presidentPassword));
                newAdmin.setGroupId(saved.getId());
                newAdmin.setRole(com.bachatgat.model.Role.PRESIDENT);
                if (presidentEmail != null) newAdmin.setEmail(presidentEmail);
                if (presidentMobile != null) newAdmin.setMobileNumber(presidentMobile);
                boolean isTemporary = (dto.getTemporaryAdminPassword() != null && !dto.getTemporaryAdminPassword().isBlank())
                        || "InitialP@ss123".equals(presidentPassword);
                newAdmin.setFirstLogin(isTemporary);
            } else {
                newAdmin = new com.bachatgat.model.User(
                        presidentUserLogin,
                        passwordEncoder.encode(presidentPassword),
                        presidentEmail != null ? presidentEmail : presidentUserLogin + "@bachatgat.org",
                        dto.getPresident() != null ? dto.getPresident() : "Group President",
                        com.bachatgat.model.Role.PRESIDENT,
                        saved.getId(),
                        null
                );
                newAdmin.setFullNameMr(dto.getPresidentNameMr() != null ? dto.getPresidentNameMr() : dto.getPresident());
                newAdmin.setFullNameHi(dto.getPresidentNameHi() != null ? dto.getPresidentNameHi() : dto.getPresident());
                newAdmin.setMobileNumber(presidentMobile);
                newAdmin.setEmail(presidentEmail);
                newAdmin.setDesignation("PRESIDENT");
                boolean isTemporary = (dto.getTemporaryAdminPassword() != null && !dto.getTemporaryAdminPassword().isBlank())
                        || "InitialP@ss123".equals(presidentPassword);
                newAdmin.setFirstLogin(isTemporary);
            }
            newAdmin.setDesignation("PRESIDENT");
            dataService.saveUser(newAdmin);

            saved.setAssignedAdminId(newAdmin.getId());
            saved.setAssignedAdminName(newAdmin.getFullName());
            saved.setPresident(newAdmin.getFullName());
            saved.setPresidentNameMr(newAdmin.getFullNameMr());
            saved.setPresidentNameHi(newAdmin.getFullNameHi());
            saved.setPresidentMobile(newAdmin.getMobileNumber());
            saved.setPresidentUsername(newAdmin.getUsername());
            dataService.saveGroup(saved);
        }

        // Initialize GroupMasterData for the newly created group
        com.bachatgat.model.GroupMasterData md = new com.bachatgat.model.GroupMasterData(
                saved.getId(),
                1,
                saved.getMonthlyShareAmount() != null ? saved.getMonthlyShareAmount() : java.math.BigDecimal.valueOf(3300),
                saved.getMonthlyShareAmount() != null ? saved.getMonthlyShareAmount() : java.math.BigDecimal.valueOf(3300),
                saved.getDefaultInterestRate() != null ? saved.getDefaultInterestRate() : java.math.BigDecimal.valueOf(12.0),
                saved.getMaxLoanAmount() != null ? saved.getMaxLoanAmount() : java.math.BigDecimal.valueOf(50000),
                saved.getLateFeeAmount() != null ? saved.getLateFeeAmount() : java.math.BigDecimal.valueOf(50),
                saved.getCollectionDueDay() > 0 ? saved.getCollectionDueDay() : 10,
                saved.getGracePeriodDays() > 0 ? saved.getGracePeriodDays() : 5,
                createdBy
        );
        md.setFinancialYear("2026-2027");
        md.setDefaultLanguage(saved.getDefaultLanguage() != null ? saved.getDefaultLanguage() : "en");
        md.setAllowMultipleLoans(saved.isAllowMultipleLoans());
        md.setMaxActiveLoans(saved.getMaxActiveLoans() > 0 ? saved.getMaxActiveLoans() : 2);
        md.setMaxOutstandingLoanAmount(saved.getMaxOutstandingLoanAmount());
        md.setMaxLoanAmountPerMember(saved.getMaxLoanAmountPerMember());
        dataService.saveMasterData(md);

        auditService.log(saved.getId(), createdBy, createdBy, "GROUP_CREATED", "GROUP",
                saved.getId(), null, saved.getGroupName(), "127.0.0.1");

        return saved;
    }

    public Group updateGroup(String id, GroupDTO dto, String updatedBy) {
        Group group = getGroupById(id);
        String oldState = group.getGroupName() + " | Monthly Bachat: " + group.getMonthlyShareAmount() + " | Rate: " + group.getDefaultInterestRate() + "%";

        mapDtoToGroup(dto, group);
        validateLoanLimits(group);
        Group updated = dataService.saveGroup(group);

        // Sync latest GroupMasterData with updated Group rules
        dataService.getLatestMasterDataByGroupId(id).ifPresent(md -> {
            if (updated.getMonthlyShareAmount() != null) md.setMonthlyShareAmount(updated.getMonthlyShareAmount());
            if (updated.getDefaultInterestRate() != null) md.setLoanInterestRate(updated.getDefaultInterestRate());
            if (updated.getLoanInterestType() != null) md.setLoanInterestType(updated.getLoanInterestType());
            if (updated.getMaxLoanAmount() != null) md.setMaxLoanAmount(updated.getMaxLoanAmount());
            if (updated.getLateFeeAmount() != null) md.setLatePaymentFee(updated.getLateFeeAmount());
            if (updated.getGracePeriodDays() > 0) md.setGracePeriodDays(updated.getGracePeriodDays());
            md.setAllowMultipleLoans(updated.isAllowMultipleLoans());
            md.setMaxActiveLoans(updated.getMaxActiveLoans());
            md.setMaxOutstandingLoanAmount(updated.getMaxOutstandingLoanAmount());
            md.setMaxLoanAmountPerMember(updated.getMaxLoanAmountPerMember());
            dataService.saveMasterData(md);
        });

        String newState = updated.getGroupName() + " | Monthly Bachat: " + updated.getMonthlyShareAmount() + " | Rate: " + updated.getDefaultInterestRate() + "%";
        auditService.log(id, updatedBy, updatedBy, "GROUP_UPDATED", "GROUP", id, oldState, newState, "127.0.0.1");

        return updated;
    }

    public com.bachatgat.model.User designateOfficeBearer(String groupId, com.bachatgat.dto.OfficeBearerDTO dto, String createdBy) {
        Group group = getGroupById(groupId);
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (dto.getConfirmPassword() != null && !dto.getConfirmPassword().isBlank()
                && !dto.getPassword().equals(dto.getConfirmPassword().trim())) {
            throw new IllegalArgumentException("Password and confirmation password do not match");
        }

        String desig = (dto.getDesignation() != null ? dto.getDesignation().trim().toUpperCase() : "MEMBER");
        com.bachatgat.model.Role role;
        if ("SECRETARY".equalsIgnoreCase(desig)) {
            role = com.bachatgat.model.Role.SECRETARY;
            group.setSecretary(dto.getFullName().trim());
        } else if ("TREASURER".equalsIgnoreCase(desig)) {
            role = com.bachatgat.model.Role.TREASURER;
            group.setTreasurer(dto.getFullName().trim());
        } else {
            role = com.bachatgat.model.Role.MEMBER;
        }

        // Find or create User
        com.bachatgat.model.User user = dataService.findUserByUsername(dto.getUsername())
                .orElse(new com.bachatgat.model.User());

        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
        user.setFullName(dto.getFullName().trim());
        user.setFullNameMr(dto.getFullNameMr() != null ? dto.getFullNameMr().trim() : dto.getFullName().trim());
        user.setFullNameHi(dto.getFullNameHi() != null ? dto.getFullNameHi().trim() : dto.getFullName().trim());
        user.setMobileNumber(dto.getMobileNumber());
        user.setEmail(dto.getEmail());
        user.setRole(role);
        user.setDesignation(desig);
        user.setGroupId(groupId);
        user.setActive(dto.isActive());
        user.setFirstLogin(false);

        com.bachatgat.model.User savedUser = dataService.saveUser(user);
        dataService.saveGroup(group);

        auditService.log(groupId, createdBy, createdBy, "OFFICE_BEARER_DESIGNATED", "USER",
                savedUser.getId(), null, "Designated " + savedUser.getFullName() + " as " + desig, "127.0.0.1");

        return savedUser;
    }

    private void mapDtoToGroup(GroupDTO dto, Group group) {
        group.setGroupName(dto.getGroupName());
        if (dto.getGroupNameMr() != null) group.setGroupNameMr(dto.getGroupNameMr());
        if (dto.getGroupNameHi() != null) group.setGroupNameHi(dto.getGroupNameHi());
        if (dto.getDefaultLanguage() != null) group.setDefaultLanguage(dto.getDefaultLanguage());
        if (dto.getRegistrationId() != null) group.setRegistrationId(dto.getRegistrationId());
        group.setBachatGatRegNumber(dto.getBachatGatRegNumber());
        if (dto.getGroupCode() != null) group.setGroupCode(dto.getGroupCode());
        group.setOrganizationId(dto.getOrganizationId());
        group.setFormationDate(dto.getFormationDate());
        group.setRegistrationDate(dto.getRegistrationDate());
        group.setVillage(dto.getVillage());
        group.setTaluka(dto.getTaluka());
        group.setDistrict(dto.getDistrict());
        group.setState(dto.getState() != null ? dto.getState() : "Maharashtra");
        group.setPinCode(dto.getPinCode());
        group.setMeetingDay(dto.getMeetingDay());
        group.setMeetingTime(dto.getMeetingTime());
        group.setMeetingLocation(dto.getMeetingLocation());
        if (dto.getMonthlyShareAmount() != null) group.setMonthlyShareAmount(dto.getMonthlyShareAmount());
        if (dto.getCollectionDueDay() != null && dto.getCollectionDueDay() > 0) group.setCollectionDueDay(dto.getCollectionDueDay());
        if (dto.getDefaultInterestRate() != null) group.setDefaultInterestRate(dto.getDefaultInterestRate());
        if (dto.getLoanInterestType() != null && !dto.getLoanInterestType().isBlank()) group.setLoanInterestType(dto.getLoanInterestType());
        if (dto.getMaxLoanAmount() != null) group.setMaxLoanAmount(dto.getMaxLoanAmount());
        if (dto.getGracePeriodDays() > 0) group.setGracePeriodDays(dto.getGracePeriodDays());
        if (dto.getLateFeeAmount() != null) group.setLateFeeAmount(dto.getLateFeeAmount());
        group.setAllowMultipleLoans(dto.isAllowMultipleLoans());
        if (dto.getMaxActiveLoans() != null && dto.getMaxActiveLoans() > 0) group.setMaxActiveLoans(dto.getMaxActiveLoans());
        if (dto.getMaxOutstandingLoanAmount() != null) group.setMaxOutstandingLoanAmount(dto.getMaxOutstandingLoanAmount());
        if (dto.getMaxLoanAmountPerMember() != null) group.setMaxLoanAmountPerMember(dto.getMaxLoanAmountPerMember());
        group.setPresident(dto.getPresident());
        if (dto.getPresidentNameMr() != null) group.setPresidentNameMr(dto.getPresidentNameMr());
        if (dto.getPresidentNameHi() != null) group.setPresidentNameHi(dto.getPresidentNameHi());
        if (dto.getPresidentMobile() != null) group.setPresidentMobile(dto.getPresidentMobile());
        if (dto.getPresidentEmail() != null) group.setPresidentEmail(dto.getPresidentEmail());
        if (dto.getPresidentUsername() != null) group.setPresidentUsername(dto.getPresidentUsername());
        group.setSecretary(dto.getSecretary());
        group.setTreasurer(dto.getTreasurer());
        group.setContactNumber(dto.getContactNumber());
        group.setEmail(dto.getEmail());
        if (dto.getAssignedAdminId() != null) group.setAssignedAdminId(dto.getAssignedAdminId());
        if (dto.getAssignedAdminName() != null) group.setAssignedAdminName(dto.getAssignedAdminName());
        group.setBankName(dto.getBankName());
        group.setBranch(dto.getBranch());
        group.setAccountNumber(dto.getAccountNumber());
        group.setIfsc(dto.getIfsc());
        group.setUpiDetails(dto.getUpiDetails());
        if (dto.getStatus() != null) group.setStatus(dto.getStatus());
    }

    private void validateLoanLimits(Group group) {
        if (group.getMaxLoanAmount() != null && group.getMaxLoanAmount().signum() <= 0) {
            throw new IllegalArgumentException("Maximum group loan capacity must be greater than zero.");
        }
        if (group.getMaxLoanAmountPerMember() != null && group.getMaxLoanAmountPerMember().signum() <= 0) {
            throw new IllegalArgumentException("Maximum loan per member must be greater than zero.");
        }
        if (group.getMaxLoanAmount() != null && group.getMaxLoanAmountPerMember() != null
                && group.getMaxLoanAmountPerMember().compareTo(group.getMaxLoanAmount()) > 0) {
            throw new IllegalArgumentException("Maximum loan per member cannot exceed the group's maximum loan capacity.");
        }
    }
}
