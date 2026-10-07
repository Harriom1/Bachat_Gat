package com.bachatgat.service;

import com.bachatgat.dto.MemberDTO;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class MemberService {

    private final FirestoreDataService dataService;
    private final TransactionService transactionService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public MemberService(FirestoreDataService dataService, TransactionService transactionService,
                         AuditService auditService, PasswordEncoder passwordEncoder) {
        this.dataService = dataService;
        this.transactionService = transactionService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Member> getAllMembers() {
        List<Member> members = dataService.getAllMembers();
        members.forEach(this::alignMonthlyBachatWithGroupRule);
        return members;
    }

    public List<Member> getMembers(String groupId) {
        List<Member> members = dataService.getMembersByGroupId(groupId);
        members.forEach(this::alignMonthlyBachatWithGroupRule);
        return members;
    }

    public List<Member> getMembersByGroupId(String groupId) {
        return getMembers(groupId);
    }

    public Member getMemberById(String id) {
        Member member = dataService.findMemberById(id)
                .or(() -> dataService.findMemberByMemberId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + id));
        return alignMonthlyBachatWithGroupRule(member);
    }

    public Member getMemberByMemberId(String memberId) {
        Member member = dataService.findMemberByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with Member ID: " + memberId));
        return alignMonthlyBachatWithGroupRule(member);
    }

    public Member createMember(MemberDTO dto, String createdBy) {
        Group group = dataService.findGroupById(dto.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + dto.getGroupId()));

        // Check duplicate mobile or PAN in the same group
        List<Member> existing = dataService.getMembersByGroupId(dto.getGroupId());
        for (Member m : existing) {
            if (m.getMobileNumber() != null && m.getMobileNumber().equalsIgnoreCase(dto.getMobileNumber().trim())) {
                throw new DuplicateRecordException("Member with mobile number " + dto.getMobileNumber() + " already exists in this group");
            }
            if (dto.getPanNumber() != null && m.getPanNumber() != null && m.getPanNumber().equalsIgnoreCase(dto.getPanNumber().trim())) {
                throw new DuplicateRecordException("Member with PAN " + dto.getPanNumber() + " already exists in this group");
            }
        }

        // Auto-generate Member Registration ID if not provided
        if (dto.getRegistrationId() == null || dto.getRegistrationId().isBlank()) {
            dto.setRegistrationId(dataService.generateNextMemberRegistrationId(dto.getGroupId()));
        }

        // Generate auto Member ID: e.g. MPBG-M021
        int nextNum = existing.size() + 1;
        String prefix = group.getGroupCode() != null ? group.getGroupCode() : "SHG";
        String memberIdCode = String.format("%s-M%03d", prefix, nextNum);

        Member member = new Member();
        mapDtoToMember(dto, member);
        member.setMemberId(memberIdCode);
        member.setRegistrationId(dto.getRegistrationId());
        member.setGroupId(group.getId());
        member.setGroupName(group.getGroupName());
        member.setStatus(MemberStatus.ACTIVE);
        member.setCreatedBy(createdBy);

        // Monthly Bachat is a group rule. The member form cannot override the
        // amount configured by the group's President in Master Data.
        BigDecimal groupMonthlyBachat = getEffectiveGroupMonthlyBachat(group);
        member.setMonthlyBachatAmount(groupMonthlyBachat);
        member.setTotalSavingsTarget(groupMonthlyBachat.multiply(BigDecimal.valueOf(12)));

        // Initial financial setup
        BigDecimal initialShare = dto.getInitialShareAmount() != null ? dto.getInitialShareAmount() : BigDecimal.ZERO;
        member.setInitialShareAmount(initialShare);
        member.setCurrentShareBalance(initialShare);
        member.setTotalSavingsBalance(initialShare);

        Member saved = dataService.saveMember(member);

        // Record initial share contribution transaction if > 0
        if (initialShare.compareTo(BigDecimal.ZERO) > 0) {
            transactionService.recordTransaction(
                    group.getId(), saved.getMemberId(), saved.getFullName(), null,
                    TransactionType.SHARE_CONTRIBUTION, initialShare, initialShare, BigDecimal.ZERO,
                    LocalDate.now(), "INIT-CONTRIB", "Initial share contribution during registration",
                    "CASH", createdBy
            );
        }

        // Provision member login credentials with proper password validation & secure hashing
        String memberPassword = (dto.getPassword() != null && !dto.getPassword().isBlank())
                ? dto.getPassword().trim()
                : ((dto.getTemporaryPassword() != null && !dto.getTemporaryPassword().isBlank())
                    ? dto.getTemporaryPassword().trim() : null);

        if (dto.isCreateLoginAccount() || memberPassword != null) {
            if (memberPassword == null || memberPassword.isBlank()) {
                memberPassword = "tempPass123";
            }
            if (dto.getConfirmPassword() != null && !dto.getConfirmPassword().isBlank()
                    && !memberPassword.equals(dto.getConfirmPassword().trim())) {
                throw new com.bachatgat.exception.InvalidFinancialOperationException("Member password and confirm password do not match");
            }
            if (memberPassword.length() < 6) {
                throw new com.bachatgat.exception.InvalidFinancialOperationException("Member password must be at least 6 characters long");
            }

            String username = (dto.getLoginUsername() != null && !dto.getLoginUsername().isBlank())
                    ? dto.getLoginUsername().trim()
                    : saved.getMobileNumber();

            boolean isExplicitPassword = dto.getPassword() != null && !dto.getPassword().isBlank();
            Optional<User> existingUser = dataService.findUserByUsername(username);
            User user;
            if (existingUser.isPresent()) {
                user = existingUser.get();
                user.setPassword(passwordEncoder.encode(memberPassword));
                user.setGroupId(group.getId());
                user.setMemberId(saved.getMemberId());
                user.setFullName(saved.getFullName());
                user.setMobileNumber(saved.getMobileNumber());
                user.setEmail(saved.getEmail());
                user.setRole(Role.MEMBER);
                user.setDesignation("MEMBER");
                user.setActive(true);
                user.setFirstLogin(!isExplicitPassword);
            } else {
                user = new User(username, passwordEncoder.encode(memberPassword), saved.getEmail(),
                        saved.getFullName(), Role.MEMBER, group.getId(), saved.getMemberId());
                user.setFullNameMr(saved.getFullNameMr());
                user.setFullNameHi(saved.getFullNameHi());
                user.setMobileNumber(saved.getMobileNumber());
                user.setDesignation("MEMBER");
                user.setFirstLogin(!isExplicitPassword);
            }
            dataService.saveUser(user);

            saved.setHasLoginAccount(true);
            saved.setLoginUsername(username);
            dataService.saveMember(saved);
        }

        auditService.log(group.getId(), createdBy, createdBy, "MEMBER_CREATED", "MEMBER",
                saved.getMemberId(), null, saved.getFullName() + " registered", "127.0.0.1");

        return saved;
    }

    public Member enrollExistingMember(String groupId, String memberIdOrId, BigDecimal monthlyShare,
                                       BigDecimal committedAmount, LocalDate startDate, LocalDate endDate,
                                       String enrolledBy) {
        Group group = dataService.findGroupById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));

        Member member = dataService.findMemberById(memberIdOrId)
                .or(() -> dataService.findMemberByMemberId(memberIdOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberIdOrId));

        if (groupId.equals(member.getGroupId()) && member.getStatus() == MemberStatus.ACTIVE) {
            throw new DuplicateRecordException("Member " + member.getFullName() + " (" + member.getMemberId() + ") is already an active member of " + group.getGroupName());
        }

        String oldGroup = member.getGroupName();
        member.setGroupId(group.getId());
        member.setGroupName(group.getGroupName());
        member.setStatus(MemberStatus.ACTIVE);

        // Ignore member-specific amounts. The active group Master Data rule is
        // the only amount used for all members' current/future dues.
        BigDecimal groupMonthlyBachat = getEffectiveGroupMonthlyBachat(group);
        member.setMonthlyBachatAmount(groupMonthlyBachat);
        member.setTotalSavingsTarget(groupMonthlyBachat.multiply(BigDecimal.valueOf(12)));
        if (startDate != null) {
            member.setBachatStartDate(startDate);
        }
        if (endDate != null) {
            member.setBachatEndDate(endDate);
        }
        member.setNextDueDate(LocalDate.now().withDayOfMonth(10));

        Member updated = dataService.saveMember(member);

        dataService.findUserByUsername(member.getMobileNumber()).ifPresent(u -> {
            u.setGroupId(group.getId());
            dataService.saveUser(u);
        });

        auditService.log(group.getId(), enrolledBy, enrolledBy, "MEMBER_ENROLLED", "MEMBER",
                member.getMemberId(), oldGroup, "Enrolled into " + group.getGroupName(), "127.0.0.1");

        return updated;
    }

    public void removeMemberFromGroup(String groupId, String memberIdOrId, String removedBy) {
        Member member = dataService.findMemberById(memberIdOrId)
                .or(() -> dataService.findMemberByMemberId(memberIdOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberIdOrId));

        if (!groupId.equals(member.getGroupId())) {
            throw new IllegalArgumentException("Member does not belong to group ID: " + groupId);
        }

        if (member.getCurrentLoanOutstanding() != null && member.getCurrentLoanOutstanding().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot remove member with outstanding loan balance of ₹" + member.getCurrentLoanOutstanding() + ". Please settle loan first.");
        }

        if (member.getTotalSavingsBalance() == null || member.getTotalSavingsBalance().compareTo(BigDecimal.ZERO) <= 0) {
            dataService.deleteMember(member.getId());
        } else {
            member.setStatus(MemberStatus.EXITED);
            dataService.saveMember(member);
        }

        auditService.log(groupId, removedBy, removedBy, "MEMBER_REMOVED", "MEMBER",
                member.getMemberId(), "ACTIVE", "REMOVED/EXITED", "127.0.0.1");
    }

    public Member updateMember(String id, MemberDTO dto, String updatedBy) {
        Member member = getMemberById(id);
        String oldState = member.getFullName() + " | " + member.getMobileNumber();

        // Update editable fields without overwriting financial balances
        member.setFullName(dto.getFullName());
        member.setGuardianName(dto.getGuardianName());
        member.setGender(dto.getGender());
        member.setDob(dto.getDob());
        member.setMobileNumber(dto.getMobileNumber());
        member.setAlternateMobile(dto.getAlternateMobile());
        member.setEmail(dto.getEmail());
        member.setAddress(dto.getAddress());
        member.setVillage(dto.getVillage());
        member.setTaluka(dto.getTaluka());
        member.setDistrict(dto.getDistrict());
        member.setState(dto.getState());
        member.setPinCode(dto.getPinCode());
        member.setOccupation(dto.getOccupation());
        
        member.setPanNumber(dto.getPanNumber());
        member.setAadhaarReference(dto.getAadhaarReference());
        member.setOtherIdType(dto.getOtherIdType());
        member.setOtherIdReference(dto.getOtherIdReference());

        member.setNomineeName(dto.getNomineeName());
        member.setNomineeRelation(dto.getNomineeRelation());
        member.setNomineeMobile(dto.getNomineeMobile());
        member.setNomineeAddress(dto.getNomineeAddress());

        member.setBankName(dto.getBankName());
        member.setAccountNumber(dto.getAccountNumber());
        member.setIfsc(dto.getIfsc());
        member.setBranch(dto.getBranch());

        // Monthly Bachat is controlled by the group, not by an individual
        // member profile edit.
        Group memberGroup = dataService.findGroupById(member.getGroupId()).orElse(null);
        if (memberGroup != null) {
            BigDecimal groupMonthlyBachat = getEffectiveGroupMonthlyBachat(memberGroup);
            member.setMonthlyBachatAmount(groupMonthlyBachat);
            member.setTotalSavingsTarget(groupMonthlyBachat.multiply(BigDecimal.valueOf(12)));
        }
        if (dto.getTotalSavingsTarget() != null) {
            member.setTotalSavingsTarget(dto.getTotalSavingsTarget());
        }
        if (dto.getBachatStartDate() != null) {
            member.setBachatStartDate(dto.getBachatStartDate());
        }
        if (dto.getBachatEndDate() != null) {
            member.setBachatEndDate(dto.getBachatEndDate());
        }
        if (dto.getNextDueDate() != null) {
            member.setNextDueDate(dto.getNextDueDate());
        }

        Member updated = dataService.saveMember(member);
        String newState = updated.getFullName() + " | " + updated.getMobileNumber();

        auditService.log(member.getGroupId(), updatedBy, updatedBy, "MEMBER_UPDATED", "MEMBER",
                member.getMemberId(), oldState, newState, "127.0.0.1");

        return updated;
    }

    /** Normalize legacy registration numbers for one group to 000001, 000002, ... . */
    public List<Member> renumberGroupRegistrationIds(String groupId, String updatedBy) {
        List<Member> groupMembers = new ArrayList<>(dataService.getMembersByGroupId(groupId));
        groupMembers.sort(Comparator.comparing(Member::getMemberId, Comparator.nullsLast(String::compareTo)));
        int sequence = 1;
        int year = LocalDate.now().getYear();
        for (Member member : groupMembers) {
            member.setRegistrationId(String.format("MEM-%d-%06d", year, sequence++));
            dataService.saveMember(member);
        }
        if (!groupMembers.isEmpty()) {
            auditService.log(groupId, updatedBy, updatedBy, "MEMBER_REGISTRATION_IDS_NORMALIZED", "GROUP",
                    groupId, "LEGACY/GLOBAL SEQUENCE", "GROUP-LOCAL SEQUENCE", "127.0.0.1");
        }
        return groupMembers;
    }

    private BigDecimal getEffectiveGroupMonthlyBachat(Group group) {
        return dataService.getMasterDataForDate(group.getId(), LocalDate.now())
                .map(GroupMasterData::getMonthlyBachatAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .orElse(group.getMonthlyBachatAmount() != null
                        ? group.getMonthlyBachatAmount() : BigDecimal.valueOf(5000));
    }

    private Member alignMonthlyBachatWithGroupRule(Member member) {
        if (member != null && member.getGroupId() != null) {
            dataService.findGroupById(member.getGroupId()).ifPresent(group -> {
                BigDecimal groupMonthlyBachat = getEffectiveGroupMonthlyBachat(group);
                member.setMonthlyBachatAmount(groupMonthlyBachat);
                member.setTotalSavingsTarget(groupMonthlyBachat.multiply(BigDecimal.valueOf(12)));
            });
        }
        return member;
    }

    public Member updateStatus(String id, MemberStatus newStatus, String updatedBy) {
        Member member = getMemberById(id);
        MemberStatus oldStatus = member.getStatus();
        member.setStatus(newStatus);
        Member updated = dataService.saveMember(member);

        auditService.log(member.getGroupId(), updatedBy, updatedBy, "MEMBER_STATUS_CHANGED", "MEMBER",
                member.getMemberId(), oldStatus.name(), newStatus.name(), "127.0.0.1");

        return updated;
    }

    public Map<String, Object> getMemberLoginStatus(String idOrMemberId) {
        Member member = getMemberById(idOrMemberId);
        Optional<User> userOpt = dataService.findUserByMemberId(member.getMemberId())
                .or(() -> dataService.findUserByUsername(member.getMobileNumber()))
                .or(() -> (member.getLoginUsername() != null && !member.getLoginUsername().isBlank())
                        ? dataService.findUserByUsername(member.getLoginUsername())
                        : Optional.empty());

        Map<String, Object> status = new HashMap<>();
        status.put("memberId", member.getMemberId());
        status.put("fullName", member.getFullName());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            status.put("hasLoginAccount", true);
            status.put("hasLogin", true);
            status.put("username", user.getUsername());
            status.put("active", user.isActive());
            status.put("firstLogin", user.isFirstLogin());
            status.put("userId", user.getId());
        } else {
            status.put("hasLoginAccount", false);
            status.put("hasLogin", false);
            status.put("username", null);
            status.put("active", false);
            status.put("firstLogin", false);
        }
        return status;
    }

    public Map<String, Object> createMemberLogin(String idOrMemberId, String username, String temporaryPassword, String adminUsername) {
        Member member = getMemberById(idOrMemberId);
        String finalUsername = (username != null && !username.isBlank()) ? username.trim() : member.getMobileNumber();

        dataService.findUserByUsername(finalUsername).ifPresent(u -> {
            throw new DuplicateRecordException("Username or mobile '" + finalUsername + "' is already registered as a login account");
        });

        User user = new User(finalUsername, passwordEncoder.encode(temporaryPassword), member.getEmail(),
                member.getFullName(), Role.USER, member.getGroupId(), member.getMemberId());
        user.setFullNameMr(member.getFullNameMr());
        user.setFullNameHi(member.getFullNameHi());
        user.setMobileNumber(member.getMobileNumber());
        user.setFirstLogin(true); // Mandatory first-login password change
        dataService.saveUser(user);

        member.setHasLoginAccount(true);
        member.setLoginUsername(finalUsername);
        dataService.saveMember(member);

        auditService.log(member.getGroupId(), adminUsername, adminUsername, "MEMBER_LOGIN_CREATED", "USER",
                user.getId(), null, "Created login account for member " + member.getMemberId(), "127.0.0.1");

        Map<String, Object> res = new HashMap<>();
        res.put("username", finalUsername);
        res.put("firstLogin", true);
        res.put("message", "Login account created successfully. User must change temporary password on first login.");
        return res;
    }

    public void resetMemberPassword(String idOrMemberId, String temporaryPassword, String adminUsername) {
        Member member = getMemberById(idOrMemberId);
        List<User> userList = new ArrayList<>();
        if (member.getLoginUsername() != null) {
            dataService.findUserByUsername(member.getLoginUsername()).ifPresent(userList::add);
        }
        dataService.findUserByMemberId(member.getMemberId()).ifPresent(u -> {
            if (!userList.contains(u)) userList.add(u);
        });
        dataService.findUserByUsername(member.getMobileNumber()).ifPresent(u -> {
            if (!userList.contains(u)) userList.add(u);
        });

        if (userList.isEmpty()) {
            throw new ResourceNotFoundException("No login account found for member " + member.getMemberId());
        }

        String encoded = passwordEncoder.encode(temporaryPassword);
        for (User user : userList) {
            user.setPassword(encoded);
            user.setFirstLogin(true); // Force password change upon next login
            dataService.saveUser(user);
            auditService.log(member.getGroupId(), adminUsername, adminUsername, "MEMBER_PASSWORD_RESET", "USER",
                    user.getId(), null, "Admin reset temporary password for member " + member.getMemberId() + " (user: " + user.getUsername() + ")", "127.0.0.1");
        }
    }

    public void toggleMemberAccountStatus(String idOrMemberId, boolean active, String adminUsername) {
        Member member = getMemberById(idOrMemberId);
        List<User> userList = new ArrayList<>();
        if (member.getLoginUsername() != null) {
            dataService.findUserByUsername(member.getLoginUsername()).ifPresent(userList::add);
        }
        dataService.findUserByMemberId(member.getMemberId()).ifPresent(u -> {
            if (!userList.contains(u)) userList.add(u);
        });
        dataService.findUserByUsername(member.getMobileNumber()).ifPresent(u -> {
            if (!userList.contains(u)) userList.add(u);
        });

        if (userList.isEmpty()) {
            throw new ResourceNotFoundException("No login account found for member " + member.getMemberId());
        }

        for (User user : userList) {
            user.setActive(active);
            dataService.saveUser(user);
            auditService.log(member.getGroupId(), adminUsername, adminUsername, "MEMBER_ACCOUNT_STATUS_TOGGLED", "USER",
                    user.getId(), null, "Account active status set to " + active + " (user: " + user.getUsername() + ")", "127.0.0.1");
        }
    }

    private void mapDtoToMember(MemberDTO dto, Member member) {
        member.setFullName(dto.getFullName());
        if (dto.getFullNameMr() != null) member.setFullNameMr(dto.getFullNameMr());
        if (dto.getFullNameHi() != null) member.setFullNameHi(dto.getFullNameHi());
        if (dto.getRegistrationId() != null) member.setRegistrationId(dto.getRegistrationId());
        member.setGuardianName(dto.getGuardianName());
        member.setGender(dto.getGender() != null ? dto.getGender() : "FEMALE");
        member.setDob(dto.getDob());
        member.setMobileNumber(dto.getMobileNumber());
        member.setAlternateMobile(dto.getAlternateMobile());
        member.setEmail(dto.getEmail());
        member.setAddress(dto.getAddress());
        member.setVillage(dto.getVillage());
        member.setTaluka(dto.getTaluka());
        member.setDistrict(dto.getDistrict());
        member.setState(dto.getState() != null ? dto.getState() : "Maharashtra");
        member.setPinCode(dto.getPinCode());
        member.setOccupation(dto.getOccupation());
        member.setJoiningDate(dto.getJoiningDate() != null ? dto.getJoiningDate() : LocalDate.now());
        member.setMonthlyShareAmount(dto.getMonthlyShareAmount() != null ? dto.getMonthlyShareAmount() : BigDecimal.valueOf(5000));
        member.setMonthlyCommittedAmount(dto.getMonthlyCommittedAmount() != null ? dto.getMonthlyCommittedAmount() : member.getMonthlyShareAmount());
        member.setMembershipFee(dto.getMembershipFee() != null ? dto.getMembershipFee() : BigDecimal.valueOf(200));
        member.setTotalSavingsTarget(dto.getTotalSavingsTarget() != null ? dto.getTotalSavingsTarget() : BigDecimal.valueOf(60000));
        member.setBachatStartDate(dto.getBachatStartDate() != null ? dto.getBachatStartDate() : LocalDate.now());
        member.setBachatEndDate(dto.getBachatEndDate() != null ? dto.getBachatEndDate() : LocalDate.now().plusMonths(12));
        member.setNextDueDate(dto.getNextDueDate() != null ? dto.getNextDueDate() : LocalDate.now().withDayOfMonth(10));

        member.setPanNumber(dto.getPanNumber());
        member.setAadhaarReference(dto.getAadhaarReference());
        member.setOtherIdType(dto.getOtherIdType());
        member.setOtherIdReference(dto.getOtherIdReference());

        member.setNomineeName(dto.getNomineeName());
        member.setNomineeRelation(dto.getNomineeRelation());
        member.setNomineeMobile(dto.getNomineeMobile());
        member.setNomineeAddress(dto.getNomineeAddress());

        member.setBankName(dto.getBankName());
        member.setAccountNumber(dto.getAccountNumber());
        member.setIfsc(dto.getIfsc());
        member.setBranch(dto.getBranch());
    }
}
