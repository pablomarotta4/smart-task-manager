package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.AuthResponse;
import com.pablomarotta.smart_task_manager.dto.LoginRequest;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.security.JwtTokenProvider;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        String username = authentication.getName();
        String token = jwtTokenProvider.generateToken(username);
        UserResponse user = userService.getUserByUsername(username);

        return ResponseEntity.ok(new AuthResponse(token, "Bearer", user));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(registerRequest.getUsername());
        userRequest.setEmail(registerRequest.getEmail());
        userRequest.setPassword(registerRequest.getPassword());
        userRequest.setFullName(registerRequest.getFullName());

        UserResponse user = userService.createUser(userRequest);
        String token = jwtTokenProvider.generateToken(user.getUsername());

        return new AuthResponse(token, "Bearer", user);
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
}
