package com.bachatgat.dto;

public class GroupDropdownDTO {
    private String id;
    private String groupCode;
    private String groupName;
    private String groupNameMr;
    private String groupNameHi;

    public GroupDropdownDTO() {}

    public GroupDropdownDTO(String id, String groupCode, String groupName, String groupNameMr, String groupNameHi) {
        this.id = id;
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.groupNameMr = groupNameMr;
        this.groupNameHi = groupNameHi;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getGroupName() { return groupName; }
    public String getName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupNameMr() { return groupNameMr; }
    public void setGroupNameMr(String groupNameMr) { this.groupNameMr = groupNameMr; }
    public String getGroupNameHi() { return groupNameHi; }
    public void setGroupNameHi(String groupNameHi) { this.groupNameHi = groupNameHi; }
}
