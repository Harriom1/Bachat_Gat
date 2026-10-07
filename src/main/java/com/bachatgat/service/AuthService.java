package com.bachatgat.service;

import com.bachatgat.dto.ChangePasswordRequest;
import com.bachatgat.dto.GroupDropdownDTO;
import com.bachatgat.dto.LoginRequest;
import com.bachatgat.dto.LoginResponse;
import com.bachatgat.dto.MemberDropdownDTO;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.exception.UnauthorizedException;
import com.bachatgat.model.Group;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final FirestoreDataService dataService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    public AuthService(FirestoreDataService dataService, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider, AuditService auditService) {
        this.dataService = dataService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.auditService = auditService;
    }

    public List<GroupDropdownDTO> getPublicGroups() {
        return dataService.getAllGroups().stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .map(g -> new GroupDropdownDTO(
                        g.getId(),
                        g.getGroupCode() != null ? g.getGroupCode() : g.getId(),
                        g.getGroupName(),
                        g.getGroupNameMr() != null ? g.getGroupNameMr() : g.getGroupName(),
                        g.getGroupNameHi() != null ? g.getGroupNameHi() : g.getGroupName()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Public login search. Only safe group-identification fields are returned;
     * user accounts and financial data are never included in this response.
     */
    public List<GroupDropdownDTO> searchPublicGroups(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isBlank()) return List.of();

        return dataService.getAllGroups().stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .filter(g -> contains(g.getGroupName(), normalized)
                        || contains(g.getGroupCode(), normalized)
                        || contains(g.getId(), normalized)
                        || contains(g.getGroupNameMr(), normalized)
                        || contains(g.getGroupNameHi(), normalized))
                .limit(20)
                .map(g -> new GroupDropdownDTO(
                        g.getId(),
                        g.getGroupCode() != null ? g.getGroupCode() : g.getId(),
                        g.getGroupName(),
                        g.getGroupNameMr() != null ? g.getGroupNameMr() : g.getGroupName(),
                        g.getGroupNameHi() != null ? g.getGroupNameHi() : g.getGroupName()
                ))
                .collect(Collectors.toList());
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    public List<MemberDropdownDTO> getPublicGroupMembers(String groupId) {
        List<MemberDropdownDTO> list = new ArrayList<>();
        Group group = dataService.findGroupById(groupId).orElse(null);

        // 1. Include President / Group Admin user if not in members list
        if (group != null) {
            List<User> groupUsers = dataService.getAllUsers().stream()
                    .filter(u -> groupId.equals(u.getGroupId()))
                    .collect(Collectors.toList());

            User presidentUser = groupUsers.stream()
                    .filter(u -> "president".equalsIgnoreCase(u.getUsername()))
                    .findFirst()
                    .orElseGet(() -> groupUsers.stream()
                            .filter(u -> u.getRole() == Role.PRESIDENT || "PRESIDENT".equalsIgnoreCase(u.getDesignation()))
                            .findFirst().orElse(null));

            if (presidentUser != null) {
                MemberDropdownDTO presDto = new MemberDropdownDTO();
                presDto.setId(presidentUser.getId());
                presDto.setMemberId(presidentUser.getMemberId() != null ? presidentUser.getMemberId() : "PRESIDENT");
                presDto.setRegistrationId(group.getRegistrationId() != null ? group.getRegistrationId() : "PRES-01");
                presDto.setFullName(presidentUser.getFullName() != null ? presidentUser.getFullName() : group.getPresident());
                presDto.setFullNameMr(presidentUser.getFullNameMr() != null ? presidentUser.getFullNameMr() : group.getPresidentNameMr());
                presDto.setFullNameHi(presidentUser.getFullNameHi() != null ? presidentUser.getFullNameHi() : group.getPresidentNameHi());
                presDto.setMobileNumber(presidentUser.getMobileNumber() != null ? presidentUser.getMobileNumber() : group.getContactNumber());
                presDto.setHasLoginAccount(true);
                presDto.setRole("PRESIDENT");
                presDto.setDesignation("PRESIDENT");
                presDto.setUsername("president");
                list.add(presDto);
            } else if (group.getPresident() != null && !group.getPresident().isBlank()) {
                MemberDropdownDTO presDto = new MemberDropdownDTO();
                presDto.setId("president-" + groupId);
                presDto.setMemberId("PRESIDENT");
                presDto.setFullName(group.getPresident());
                presDto.setFullNameMr(group.getPresidentNameMr() != null ? group.getPresidentNameMr() : group.getPresident());
                presDto.setFullNameHi(group.getPresidentNameHi() != null ? group.getPresidentNameHi() : group.getPresident());
                presDto.setMobileNumber(group.getContactNumber());
                presDto.setHasLoginAccount(true);
                presDto.setRole("PRESIDENT");
                presDto.setDesignation("PRESIDENT");
                presDto.setUsername("president");
                list.add(presDto);
            }
        }

        // 2. Add all active members of this group
        List<Member> members = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .collect(Collectors.toList());

        for (Member m : members) {
            if (list.stream().anyMatch(d -> d.getFullName() != null && d.getFullName().equalsIgnoreCase(m.getFullName()))) {
                continue;
            }

            // Look for Secretary or Treasurer accounts first
            Optional<User> uOpt = dataService.getAllUsers().stream()
                    .filter(u -> groupId.equals(u.getGroupId())
                            && u.getRole() == Role.SECRETARY
                            && (m.getMemberId().equalsIgnoreCase(u.getMemberId()) || m.getFullName().equalsIgnoreCase(u.getFullName())))
                    .findFirst()
                    .or(() -> dataService.getAllUsers().stream()
                            .filter(u -> groupId.equals(u.getGroupId())
                                    && u.getRole() == Role.TREASURER
                                    && (m.getMemberId().equalsIgnoreCase(u.getMemberId()) || m.getFullName().equalsIgnoreCase(u.getFullName())))
                            .findFirst())
                    .or(() -> dataService.getAllUsers().stream()
                            .filter(u -> groupId.equals(u.getGroupId())
                                    && "member".equalsIgnoreCase(u.getUsername())
                                    && m.getMemberId().equalsIgnoreCase(u.getMemberId()))
                            .findFirst())
                    .or(() -> dataService.findUserByMemberId(m.getMemberId()))
                    .or(() -> dataService.findUserByUsername(m.getMobileNumber()))
                    .or(() -> (m.getLoginUsername() != null && !m.getLoginUsername().isBlank())
                            ? dataService.findUserByUsername(m.getLoginUsername())
                            : Optional.empty());

            MemberDropdownDTO dto = new MemberDropdownDTO();
            dto.setId(m.getId());
            dto.setMemberId(m.getMemberId());
            dto.setRegistrationId(m.getRegistrationId());
            dto.setFullName(m.getFullName());
            dto.setFullNameMr(m.getFullNameMr() != null ? m.getFullNameMr() : m.getFullName());
            dto.setFullNameHi(m.getFullNameHi() != null ? m.getFullNameHi() : m.getFullName());
            dto.setMobileNumber(m.getMobileNumber());
            dto.setHasLoginAccount(m.isHasLoginAccount());

            if (uOpt.isPresent()) {
                User u = uOpt.get();
                dto.setRole(u.getRole() != null ? u.getRole().name() : "MEMBER");
                dto.setDesignation(u.getDesignation() != null ? u.getDesignation() : dto.getRole());
                dto.setUsername(u.getUsername());
            } else {
                if (group != null && group.getSecretary() != null && m.getFullName().contains(group.getSecretary())) {
                    dto.setRole("SECRETARY");
                    dto.setDesignation("SECRETARY");
                    dto.setUsername("secretary");
                } else if (group != null && group.getTreasurer() != null && m.getFullName().contains(group.getTreasurer())) {
                    dto.setRole("TREASURER");
                    dto.setDesignation("TREASURER");
                    dto.setUsername("treasurer");
                } else {
                    dto.setRole("MEMBER");
                    dto.setDesignation("MEMBER");
                    dto.setUsername(m.getLoginUsername() != null ? m.getLoginUsername() : m.getMobileNumber());
                }
            }
            list.add(dto);
        }

        // Sort: President (1), Secretary (2), Treasurer (3), Members (4)
        list.sort((a, b) -> {
            int scoreA = getRoleRank(a.getRole());
            int scoreB = getRoleRank(b.getRole());
            if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
            return (a.getFullName() != null ? a.getFullName() : "").compareToIgnoreCase(b.getFullName() != null ? b.getFullName() : "");
        });

        return list;
    }

    private int getRoleRank(String role) {
        if ("PRESIDENT".equalsIgnoreCase(role)) return 1;
        if ("SECRETARY".equalsIgnoreCase(role)) return 2;
        if ("TREASURER".equalsIgnoreCase(role)) return 3;
        return 4;
    }

    public LoginResponse login(LoginRequest request) {
        User user;

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            // Direct Username / Mobile / Email Login (Super Admin, President, or Direct User)
            String ident = request.getUsername().trim();
            List<User> candidates = dataService.findUsersByIdentifier(ident);
            if (candidates.isEmpty()) {
                throw new UnauthorizedException("Invalid username or password");
            }
            user = candidates.stream()
                    .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPassword()))
                    .findFirst()
                    .orElse(candidates.get(0));
        } else if (request.getMemberId() != null && !request.getMemberId().isBlank()) {
            // Support Member Login via Group + Member Selection
            user = dataService.findUserById(request.getMemberId())
                    .or(() -> dataService.findUserByUsername(request.getMemberId()))
                    .orElse(null);

            if (user == null) {
                Member member = dataService.findMemberById(request.getMemberId())
                        .or(() -> dataService.findMemberByMemberId(request.getMemberId()))
                        .orElseThrow(() -> new UnauthorizedException("Member record not found"));

                if (request.getGroupId() != null && !request.getGroupId().isBlank()
                        && !member.getGroupId().equals(request.getGroupId())) {
                    throw new UnauthorizedException("Selected member does not belong to this Bachat Gat");
                }

                user = dataService.findUserByMemberId(member.getMemberId())
                        .or(() -> dataService.findUserByUsername(member.getMobileNumber()))
                        .or(() -> (member.getLoginUsername() != null && !member.getLoginUsername().isBlank())
                                ? dataService.findUserByUsername(member.getLoginUsername())
                                : Optional.empty())
                        .orElseThrow(() -> new UnauthorizedException("Member login account has not been configured by the Group President. Please contact your administrator."));
            }
        } else {
            throw new UnauthorizedException("Please provide username or select a member.");
        }

        // The group is a server-side authorization boundary. Even when the
        // client sends a username, never authenticate that account for a
        // different group supplied in the request.
        if (request.getGroupId() != null && !request.getGroupId().isBlank()
                && !request.getGroupId().equals(user.getGroupId())) {
            throw new UnauthorizedException("Selected user does not belong to this Bachat Gat");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("User account is inactive. Please contact your group administrator.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid password. Please check your credentials.");
        }

        String token = tokenProvider.generateToken(user);

        String groupCode = "";
        String groupName = "";
        String groupNameMr = "";
        String groupNameHi = "";
        if (user.getGroupId() != null) {
            Optional<Group> groupOpt = dataService.findGroupById(user.getGroupId());
            if (groupOpt.isPresent()) {
                Group g = groupOpt.get();
                groupCode = g.getGroupCode() != null ? g.getGroupCode() : g.getId();
                groupName = g.getGroupName();
                groupNameMr = g.getGroupNameMr() != null ? g.getGroupNameMr() : g.getGroupName();
                groupNameHi = g.getGroupNameHi() != null ? g.getGroupNameHi() : g.getGroupName();
            }
        }

        LoginResponse response = new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getGroupId(),
                groupName,
                user.getMemberId(),
                user.getPreferredLanguage()
        );
        response.setFullNameMr(user.getFullNameMr() != null ? user.getFullNameMr() : user.getFullName());
        response.setFullNameHi(user.getFullNameHi() != null ? user.getFullNameHi() : user.getFullName());
        response.setGroupCode(groupCode);
        response.setGroupNameMr(groupNameMr);
        response.setGroupNameHi(groupNameHi);
        response.setFirstLogin(user.isFirstLogin());
        String desig = user.getDesignation();
        if (desig == null || desig.isBlank()) {
            if (user.getRole() == Role.ADMIN || user.getRole() == Role.PRESIDENT) {
                desig = "PRESIDENT";
            } else if (user.getRole() == Role.SECRETARY) {
                desig = "SECRETARY";
            } else if (user.getRole() == Role.TREASURER) {
                desig = "TREASURER";
            } else if (user.getRole() == Role.MEMBER || user.getRole() == Role.USER) {
                desig = "MEMBER";
            }
        }
        response.setDesignation(desig);
        if ("PRESIDENT".equalsIgnoreCase(desig) && response.getRole() == Role.ADMIN) {
            response.setRole(Role.PRESIDENT);
        }
        if ("MEMBER".equalsIgnoreCase(desig) && response.getRole() == Role.USER) {
            response.setRole(Role.MEMBER);
        }

        return response;
    }

    public void firstLoginChangePassword(String username, com.bachatgat.dto.FirstLoginPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidFinancialOperationException("New password and confirm password do not match");
        }
        User user = dataService.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        dataService.saveUser(user);
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        User user = dataService.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidFinancialOperationException("Current password does not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        dataService.saveUser(user);
    }

    public void changeUsername(String currentUsername, com.bachatgat.dto.ChangeUsernameRequest request) {
        if (!request.getNewUsername().equals(request.getConfirmUsername())) {
            throw new InvalidFinancialOperationException("New username and confirmation do not match");
        }
        User user = dataService.findUserByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String newUsername = request.getNewUsername().trim();
        dataService.findUserByUsername(newUsername).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new InvalidFinancialOperationException("Username already exists");
            }
        });
        user.setUsername(newUsername);
        dataService.saveUser(user);
    }

    public User getCurrentUser(String username) {
        return dataService.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public LoginResponse memberFirstLoginSetup(com.bachatgat.dto.MemberFirstLoginSetupRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new InvalidFinancialOperationException("New password and confirm password do not match");
        }
        if (request.getPassword().length() < 6) {
            throw new InvalidFinancialOperationException("Password must be at least 6 characters long");
        }

        String identifier = request.getIdentifier().trim();
        String groupId = request.getGroupId().trim();

        // Locate member by memberId, mobile, or id within group
        Member member = dataService.findMemberById(identifier)
                .or(() -> dataService.findMemberByMemberId(identifier))
                .or(() -> dataService.getAllMembers().stream()
                        .filter(m -> groupId.equals(m.getGroupId())
                                && (identifier.equalsIgnoreCase(m.getMobileNumber())
                                        || identifier.equalsIgnoreCase(m.getMemberId())
                                        || identifier.equalsIgnoreCase(m.getId())))
                        .findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("Member record not found in the selected Bachat Gat"));

        if (!groupId.equals(member.getGroupId())) {
            throw new UnauthorizedException("Selected member does not belong to this Bachat Gat");
        }

        // Look up existing user account or create if none exists
        User user = dataService.findUserByMemberId(member.getMemberId())
                .or(() -> dataService.findUserByUsername(member.getMobileNumber()))
                .orElseGet(() -> {
                    User newUser = new User(member.getMobileNumber(),
                            passwordEncoder.encode(request.getPassword()),
                            member.getEmail(),
                            member.getFullName(),
                            Role.USER,
                            member.getGroupId(),
                            member.getMemberId());
                    newUser.setFullNameMr(member.getFullNameMr());
                    newUser.setFullNameHi(member.getFullNameHi());
                    newUser.setMobileNumber(member.getMobileNumber());
                    return newUser;
                });

        // Ensure account is allowed to do first-login setup
        if (!user.isFirstLogin() && user.getPassword() != null && !user.getPassword().isBlank() && member.hasLoginAccount()) {
            throw new UnauthorizedException("This account has already completed first-time setup. Please log in using your password.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstLogin(false);
        user.setActive(true);
        user.setGroupId(member.getGroupId());
        user.setMemberId(member.getMemberId());
        dataService.saveUser(user);

        member.setHasLoginAccount(true);
        member.setLoginUsername(user.getUsername());
        dataService.saveMember(member);

        auditService.log(
                member.getGroupId(),
                user.getId(),
                user.getUsername(),
                "FIRST_LOGIN_PASSWORD_SET",
                "USER",
                user.getId(),
                null,
                "First-time password set for member " + member.getMemberId(),
                "127.0.0.1"
        );

        String token = tokenProvider.generateToken(user);
        Optional<Group> groupOpt = dataService.findGroupById(user.getGroupId());
        String groupName = groupOpt.map(Group::getGroupName).orElse("");
        String groupCode = groupOpt.map(Group::getGroupCode).orElse("");

        LoginResponse response = new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getGroupId(),
                groupName,
                user.getMemberId(),
                user.getPreferredLanguage()
        );
        response.setGroupCode(groupCode);
        response.setFirstLogin(false);
        String desig = user.getDesignation();
        if (desig == null || desig.isBlank()) {
            desig = (member != null && member.getDesignation() != null) ? member.getDesignation() : "MEMBER";
        }
        response.setDesignation(desig);
        return response;
    }
}
