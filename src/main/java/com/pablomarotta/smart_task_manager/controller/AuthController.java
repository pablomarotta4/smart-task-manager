package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.AuthResponse;
import com.pablomarotta.smart_task_manager.dto.LoginRequest;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.security.JwtTokenProvider;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final long refreshExpirationMs;
    private final boolean secureRefreshCookie;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserService userService,
            RefreshTokenService refreshTokenService,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs,
            @Value("${jwt.refresh-cookie-secure}") boolean secureRefreshCookie
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.refreshExpirationMs = refreshExpirationMs;
        this.secureRefreshCookie = secureRefreshCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        String username = authentication.getName();
        return authenticatedResponse(username, HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(registerRequest.getUsername());
        userRequest.setEmail(registerRequest.getEmail());
        userRequest.setPassword(registerRequest.getPassword());
        userRequest.setFullName(registerRequest.getFullName());

        UserResponse user = userService.createUser(userRequest);
        return authenticatedResponse(user, HttpStatus.CREATED);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }
        RefreshTokenService.IssuedRefreshToken replacement = refreshTokenService.rotate(refreshToken);
        return responseWithRefreshCookie(
                replacement,
                userService.getUserByUsername(replacement.username()),
                HttpStatus.OK
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userService.getUserByUsername(authentication.getName());
    }

    private ResponseEntity<AuthResponse> authenticatedResponse(String username, HttpStatus status) {
        return authenticatedResponse(userService.getUserByUsername(username), status);
    }

    private ResponseEntity<AuthResponse> authenticatedResponse(UserResponse user, HttpStatus status) {
        return responseWithRefreshCookie(
                refreshTokenService.issueForUsername(user.getUsername()),
                user,
                status
        );
    }

    private ResponseEntity<AuthResponse> responseWithRefreshCookie(
            RefreshTokenService.IssuedRefreshToken refreshToken,
            UserResponse user,
            HttpStatus status
    ) {
        AuthResponse response = new AuthResponse(
                jwtTokenProvider.generateToken(refreshToken.username()),
                "Bearer",
                user
        );
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken.value()).toString())
                .body(response);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(secureRefreshCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureRefreshCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
