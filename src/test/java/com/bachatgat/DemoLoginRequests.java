package com.bachatgat;

import com.bachatgat.dto.LoginRequest;

public final class DemoLoginRequests {
    private DemoLoginRequests() {}

    public static LoginRequest forAccount(String username, String password) {
        return switch (username) {
            case "admin" -> inGroup(username, password, "bg-001", "usr-admin-01");
            case "groupadmin2" -> inGroup(username, password, "bg-002", "usr-groupadmin-02");
            case "user" -> inGroup(username, password, "bg-001", "MPBG-M001");
            case "9823045678" -> inGroup(username, password, "bg-001", "MPBG-M004");
            default -> throw new IllegalArgumentException("Unknown demo account: " + username);
        };
    }

    private static LoginRequest inGroup(String username, String password, String groupId, String personId) {
        LoginRequest request = new LoginRequest(groupId, personId, password);
        request.setUsername(username);
        return request;
    }
}
