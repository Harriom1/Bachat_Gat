package com.bachatgat.security;

import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UserPrincipal implements UserDetails {
    private final String id;
    private final String username;
    private final String password;
    private final String fullName;
    private final Role role;
    private final String groupId;
    private final String memberId;
    private final boolean active;
    private final String designation;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.groupId = user.getGroupId();
        this.memberId = user.getMemberId();
        this.active = user.isActive();
        this.designation = user.getDesignation();
        java.util.List<GrantedAuthority> auths = new java.util.ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (user.getRole() == Role.PRESIDENT || user.getRole() == Role.SECRETARY || user.getRole() == Role.TREASURER || user.getRole() == Role.SUPER_ADMIN) {
            auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (user.getRole() == Role.MEMBER) {
            auths.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (user.getRole() == Role.USER) {
            auths.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }
        this.authorities = java.util.Collections.unmodifiableList(auths);
    }

    public UserPrincipal(String id, String username, String password, Role role, String groupId, String memberId) {
        this(id, username, password, role, groupId, memberId, null);
    }

    public UserPrincipal(String id, String username, String password, Role role, String groupId, String memberId, String designation) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = username;
        this.role = role;
        this.groupId = groupId;
        this.memberId = memberId;
        this.active = true;
        this.designation = designation;
        java.util.List<GrantedAuthority> auths = new java.util.ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (role == Role.PRESIDENT || role == Role.SECRETARY || role == Role.TREASURER || role == Role.SUPER_ADMIN) {
            auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (role == Role.MEMBER) {
            auths.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (role == Role.USER) {
            auths.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }
        this.authorities = java.util.Collections.unmodifiableList(auths);
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public String getGroupId() { return groupId; }
    public String getMemberId() { return memberId; }
    public String getDesignation() { return designation; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
