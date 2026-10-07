package com.bachatgat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CollectionGenerateMonthlyRequest {
    @NotBlank(message = "Group ID is required")
    private String groupId;
    
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private int month;
    
    @Min(value = 2020, message = "Year must be 2020 or later")
    private int year;

    public CollectionGenerateMonthlyRequest() {}

    public CollectionGenerateMonthlyRequest(String groupId, int month, int year) {
        this.groupId = groupId;
        this.month = month;
        this.year = year;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
}
