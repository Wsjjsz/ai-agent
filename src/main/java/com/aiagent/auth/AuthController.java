package com.aiagent.auth;

import com.aiagent.auth.dto.LoginRequest;
import com.aiagent.auth.dto.PasswordSetRequest;
import com.aiagent.auth.dto.ProfileUpdateRequest;
import com.aiagent.auth.dto.SmsCodeSendRequest;
import com.aiagent.auth.dto.SmsLoginRequest;
import com.aiagent.history.ChatHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SmsCodeService smsCodeService;
    private final AvatarStorageService avatarStorageService;
    private final ChatHistoryRepository chatHistoryRepository;

    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    public AuthController(UserRepository userRepository, PasswordService passwordService, TokenService tokenService,
                          SmsCodeService smsCodeService, AvatarStorageService avatarStorageService,
                          ChatHistoryRepository chatHistoryRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.smsCodeService = smsCodeService;
        this.avatarStorageService = avatarStorageService;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String username = normalizeUsername(request.username());
        UserRepository.UserRecord userRecord = findLoginRecord(username, request.countryCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确"));
        if (!passwordService.matches(request.password(), userRecord.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
        AuthenticatedUser user = new AuthenticatedUser(
                userRecord.id(),
                userRecord.username(),
                userRecord.nickname(),
                userRecord.avatarUrl()
        );
        migrateGuestSessions(request.guestId(), user);
        return AuthResponse.of(user, tokenService.createToken(user));
    }

    @PostMapping("/sms/send")
    public Map<String, Object> sendSmsCode(@Valid @RequestBody SmsCodeSendRequest request,
                                           HttpServletRequest servletRequest) {
        smsCodeService.sendLoginCode(
                request.phone(),
                request.countryCode(),
                resolveClientIp(servletRequest),
                firstNonBlank(servletRequest.getHeader("X-Guest-Id"), servletRequest.getHeader("X-Device-Id"))
        );
        return Map.of("success", true, "message", "验证码已发送");
    }

    @PostMapping("/sms/login")
    public AuthResponse smsLogin(@Valid @RequestBody SmsLoginRequest request) {
        String phone = smsCodeService.verifyAndConsumeLoginCode(request.phone(), request.countryCode(), request.code());
        AuthenticatedUser user = userRepository.findOrCreatePhoneUser(phone);
        migrateGuestSessions(request.guestId(), user);
        return AuthResponse.of(user, tokenService.createToken(user));
    }

    @GetMapping("/me")
    public AuthenticatedUser me(HttpServletRequest request) {
        return AuthContext.requireUser(request);
    }

    @PutMapping("/me")
    public AuthenticatedUser updateProfile(HttpServletRequest request, @Valid @RequestBody ProfileUpdateRequest updateRequest) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return userRepository.updateProfile(user.id(), updateRequest.nickname().trim(), updateRequest.avatarUrl());
    }

    @PostMapping("/password")
    public Map<String, Object> setPassword(HttpServletRequest request, @Valid @RequestBody PasswordSetRequest passwordRequest) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        if (user.username() != null && user.username().startsWith("guest_")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "访客账号请先登录后再设置密码");
        }
        userRepository.updatePassword(user.id(), passwordService.hash(passwordRequest.password()));
        return Map.of("success", true, "message", "密码已设置");
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAvatar(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        String avatarUrl = avatarStorageService.save(user.id(), file);
        return Map.of("success", true, "avatarUrl", avatarUrl);
    }

    @GetMapping("/avatar/file/{fileName}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String fileName) {
        Resource resource = avatarStorageService.load(fileName);
        return ResponseEntity.ok()
                .contentType(resolveAvatarMediaType(fileName))
                .header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private Optional<UserRepository.UserRecord> findLoginRecord(String usernameOrPhone, String countryCode) {
        Optional<UserRepository.UserRecord> byUsername = userRepository.findByUsername(usernameOrPhone);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        try {
            String phone = smsCodeService.normalizePhone(usernameOrPhone, countryCode);
            return userRepository.findLoginRecordByPhone(phone);
        } catch (ResponseStatusException e) {
            if (looksLikePhoneInput(usernameOrPhone)) {
                throw e;
            }
            return Optional.empty();
        }
    }

    private boolean looksLikePhoneInput(String value) {
        return value != null && value.trim().matches("^[+\\d][\\d\\s-]*$");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private MediaType resolveAvatarMediaType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_JPEG;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void migrateGuestSessions(String guestId, AuthenticatedUser targetUser) {
        if (guestId == null || guestId.isBlank()) {
            return;
        }
        try {
            AuthenticatedUser guest = userRepository.findOrCreateGuest(guestId);
            chatHistoryRepository.transferSessions(guest.id(), targetUser.id());
        } catch (Exception ignored) {
        }
    }

    public record AuthResponse(
            String token,
            AuthenticatedUser user
    ) {
        public static AuthResponse of(AuthenticatedUser user, String token) {
            return new AuthResponse(token, user);
        }
    }
}
