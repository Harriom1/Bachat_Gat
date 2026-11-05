package com.bachatgat.service;

import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.OtpChallenge;
import com.bachatgat.repository.FirestoreDataService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class OtpVerificationService {
    private static final Logger log = LoggerFactory.getLogger(OtpVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS = 5;

    private final FirestoreDataService dataService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final ObjectProvider<FirebaseAuth> firebaseAuthProvider;

    public OtpVerificationService(FirestoreDataService dataService,
                                  ObjectProvider<JavaMailSender> mailSenderProvider,
                                  ObjectProvider<FirebaseAuth> firebaseAuthProvider,
                                  @Value("${app.mail.from:${spring.mail.username:localhost@bachatgat.local}}") String fromAddress) {
        this.dataService = dataService;
        this.mailSenderProvider = mailSenderProvider;
        this.firebaseAuthProvider = firebaseAuthProvider;
        this.fromAddress = fromAddress;
    }

    public Map<String, Object> verifyFirebaseMobile(String sessionId, String phoneNumber, String idToken) {
        FirebaseAuth firebaseAuth = firebaseAuthProvider.getIfAvailable();
        if (firebaseAuth == null) {
            throw new InvalidFinancialOperationException("Firebase mobile verification is not enabled on this environment.");
        }
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            Object tokenPhone = token.getClaims().get("phone_number");
            String verifiedPhone = tokenPhone == null ? "" : tokenPhone.toString();
            if (!normalizePhone(phoneNumber).equals(normalizePhone(verifiedPhone))) {
                throw new InvalidFinancialOperationException("The verified phone number does not match the form.");
            }
            OtpChallenge challenge = new OtpChallenge();
            challenge.setId(UUID.randomUUID().toString());
            challenge.setSessionId(sessionId);
            challenge.setChannel("FIREBASE_PHONE");
            challenge.setTargetHash(hash(normalizePhone(phoneNumber)));
            challenge.setCreatedAt(Instant.now());
            challenge.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
            challenge.setVerified(true);
            challenge.setConsumed(false);
            dataService.saveOtpChallenge(challenge);
            return Map.of("verified", true, "challengeId", challenge.getId());
        } catch (InvalidFinancialOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Firebase phone verification failed: {}", ex.getMessage());
            throw new InvalidFinancialOperationException("Firebase phone verification failed.");
        }
    }

    public void requireVerified(String challengeId, String sessionId, String channel, String target) {
        OtpChallenge challenge = dataService.findOtpChallengeById(challengeId)
                .orElseThrow(() -> new InvalidFinancialOperationException("Verification session was not found."));
        if (!sessionId.equals(challenge.getSessionId()) || !channel.equals(challenge.getChannel())
                || !challenge.isVerified() || challenge.isUsedForCreation()
                || challenge.getExpiresAt() == null || Instant.now().isAfter(challenge.getExpiresAt())) {
            throw new InvalidFinancialOperationException("Required verification is missing or expired.");
        }
        String normalized = "EMAIL".equals(channel) ? normalizeEmail(target) : normalizePhone(target);
        if (!hash(normalized).equals(challenge.getTargetHash())) {
            throw new InvalidFinancialOperationException("Verified contact does not match the submitted contact.");
        }
        challenge.setUsedForCreation(true);
        dataService.saveOtpChallenge(challenge);
    }

    public Map<String, Object> sendEmailOtp(String sessionId, String target, String previousChallengeId) {
        String email = normalizeEmail(target);
        OtpChallenge challenge = previousChallengeId == null || previousChallengeId.isBlank()
                ? new OtpChallenge()
                : dataService.findOtpChallengeById(previousChallengeId)
                    .filter(existing -> sessionId.equals(existing.getSessionId())
                            && "EMAIL".equals(existing.getChannel())
                            && hash(email).equals(existing.getTargetHash()))
                    .orElse(new OtpChallenge());

        Instant now = Instant.now();
        if (challenge.getResendAllowedAt() != null && now.isBefore(challenge.getResendAllowedAt())) {
            throw new InvalidFinancialOperationException("Please wait before requesting another email OTP.");
        }
        if (challenge.getSendCount() >= MAX_SENDS) {
            throw new InvalidFinancialOperationException("Too many OTP requests. Please start a new verification session.");
        }
        if (challenge.getId() == null) challenge.setId(UUID.randomUUID().toString());

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        challenge.setSessionId(sessionId);
        challenge.setChannel("EMAIL");
        challenge.setTargetHash(hash(email));
        challenge.setOtpHash(hash(challenge.getId() + ":" + code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(OTP_TTL));
        challenge.setResendAllowedAt(now.plus(RESEND_COOLDOWN));
        challenge.setAttempts(0);
        challenge.setSendCount(challenge.getSendCount() + 1);
        challenge.setVerified(false);
        challenge.setConsumed(false);
        dataService.saveOtpChallenge(challenge);

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) throw new InvalidFinancialOperationException("Email OTP is not configured. Start Mailpit and configure SMTP first.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Bachat Gat – Email Verification OTP");
        message.setText("Your Bachat Gat email verification OTP is: " + code
                + "\n\nThis OTP expires in 5 minutes and can be used only once.\nPlease do not share it.");
        sender.send(message);

        return Map.of("challengeId", challenge.getId(), "expiresInSeconds", OTP_TTL.toSeconds(),
                "resendAfterSeconds", RESEND_COOLDOWN.toSeconds());
    }

    public Map<String, Object> verifyEmailOtp(String challengeId, String sessionId, String target, String code) {
        OtpChallenge challenge = dataService.findOtpChallengeById(challengeId)
                .orElseThrow(() -> new InvalidFinancialOperationException("OTP verification session was not found."));
        Instant now = Instant.now();
        if (!sessionId.equals(challenge.getSessionId()) || !"EMAIL".equals(challenge.getChannel())
                || !hash(normalizeEmail(target)).equals(challenge.getTargetHash())) {
            throw new InvalidFinancialOperationException("OTP does not belong to this verification session.");
        }
        if (challenge.isConsumed() || challenge.isVerified()) {
            throw new InvalidFinancialOperationException("This OTP has already been used.");
        }
        if (challenge.getExpiresAt() == null || now.isAfter(challenge.getExpiresAt())) {
            throw new InvalidFinancialOperationException("OTP has expired. Please request a new OTP.");
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidFinancialOperationException("Too many incorrect OTP attempts. Please request a new OTP.");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!MessageDigest.isEqual(challenge.getOtpHash().getBytes(StandardCharsets.UTF_8),
                hash(challenge.getId() + ":" + code).getBytes(StandardCharsets.UTF_8))) {
            dataService.saveOtpChallenge(challenge);
            throw new InvalidFinancialOperationException("Incorrect OTP.");
        }
        challenge.setVerified(true);
        challenge.setConsumed(true);
        dataService.saveOtpChallenge(challenge);
        return Map.of("verified", true, "challengeId", challenge.getId());
    }

    private String normalizeEmail(String value) {
        if (value == null || !value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidFinancialOperationException("A valid email address is required.");
        }
        return value.trim().toLowerCase();
    }

    private String normalizePhone(String value) {
        if (value == null) throw new InvalidFinancialOperationException("A valid mobile number is required.");
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 10) digits = "91" + digits;
        if (digits.length() < 11 || digits.length() > 15) {
            throw new InvalidFinancialOperationException("A valid mobile number is required.");
        }
        return "+" + digits;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
