package com.pablomarotta.smart_task_manager.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public final class AuthenticatedUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final int authVersion;
    private final boolean active;

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String password,
            String role,
            int authVersion,
            boolean active
    ) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        this.authVersion = authVersion;
        this.active = active;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAuthVersion() {
        return authVersion;
    }

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
    public boolean isEnabled() {
        return active;
    }
}
