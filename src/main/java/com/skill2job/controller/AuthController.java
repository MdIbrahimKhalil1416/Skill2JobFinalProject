package com.skill2job.controller;

import com.skill2job.dto.AuthResponse;
import com.skill2job.dto.PasswordDtos;
import com.skill2job.dto.LoginRequest;
import com.skill2job.dto.RegisterRequest;
import com.skill2job.dto.RegisterResponse;
import com.skill2job.dto.ResendOtpRequest;
import com.skill2job.dto.ResendOtpResponse;
import com.skill2job.dto.UserResponse;
import com.skill2job.dto.VerifyOtpRequest;
import com.skill2job.security.AuthUser;
import com.skill2job.service.AuthRateLimiter;
import com.skill2job.service.AuthService;
import com.skill2job.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final AuthRateLimiter limiter;

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration QUARTER = Duration.ofMinutes(15);

    public AuthController(AuthService authService, PasswordService passwordService, AuthRateLimiter limiter) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.limiter = limiter;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sign up as STUDENT or EMPLOYER. Sends a 6-digit OTP to the email and does "
            + "NOT return a token yet - call /verify-otp to activate the account and log in. "
            + "The \"cv\" part is optional and only used for students (PDF/DOC/DOCX, max size is configurable).")
    public RegisterResponse register(@Valid @RequestPart("data") RegisterRequest request,
                                     @RequestPart(value = "cv", required = false) MultipartFile cv,
                                     HttpServletRequest http) {
        limiter.check("register-ip", limiter.clientIp(http), 60, HOUR);
        limiter.check("register-email", request.email(), 5, HOUR);
        return authService.register(request, cv);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Confirm the signup OTP. On success the account is activated and a JWT is returned.")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        limiter.check("verify-ip", limiter.clientIp(http), 120, QUARTER);
        limiter.check("verify-email", request.email(), 20, QUARTER);
        return authService.verifyOtp(request);
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Send a new OTP for a pending (not yet verified) signup. Subject to a cooldown.")
    public ResendOtpResponse resendOtp(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest http) {
        limiter.check("resend-ip", limiter.clientIp(http), 60, HOUR);
        limiter.check("resend-email", request.email(), 6, HOUR);
        return authService.resendOtp(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = limiter.clientIp(http);
        limiter.check("login-ip", ip, 300, QUARTER);
        limiter.check("login-ip-email", ip + "|" + request.email().trim(), 10, QUARTER);
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a 6-digit password reset code (same response whether or not the account exists)")
    public PasswordDtos.MessageResponse forgotPassword(@Valid @RequestBody PasswordDtos.ForgotPasswordRequest request,
                                                      HttpServletRequest http) {
        limiter.check("forgot-ip", limiter.clientIp(http), 60, HOUR);
        limiter.check("forgot-email", request.email(), 6, HOUR);
        return passwordService.forgot(request);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the emailed code. Signs out every existing session.")
    public PasswordDtos.MessageResponse resetPassword(@Valid @RequestBody PasswordDtos.ResetPasswordRequest request,
                                                     HttpServletRequest http) {
        limiter.check("reset-ip", limiter.clientIp(http), 120, QUARTER);
        limiter.check("reset-email", request.email(), 20, QUARTER);
        return passwordService.reset(request);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change my password (signed in). Returns a new token; other sessions are signed out.")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody PasswordDtos.ChangePasswordRequest request) {
        return passwordService.change(user, request);
    }

    @GetMapping("/me")
    @Operation(summary = "The currently logged-in user")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }
}
