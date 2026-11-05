package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.OtpSendRequest;
import com.bachatgat.dto.OtpVerifyRequest;
import com.bachatgat.dto.FirebaseMobileVerifyRequest;
import com.bachatgat.service.OtpVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/verification")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class VerificationController {
    private final OtpVerificationService otpService;

    public VerificationController(OtpVerificationService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendEmailOtp(@Valid @RequestBody OtpSendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Email OTP sent", otpService.sendEmailOtp(
                request.getSessionId(), request.getTarget(), request.getChallengeId())));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmailOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Email verified", otpService.verifyEmailOtp(
                request.getChallengeId(), request.getSessionId(), request.getTarget(), request.getCode())));
    }

    @PostMapping("/mobile/firebase/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyFirebaseMobile(@Valid @RequestBody FirebaseMobileVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Mobile verified", otpService.verifyFirebaseMobile(
                request.getSessionId(), request.getPhoneNumber(), request.getIdToken())));
    }
}
