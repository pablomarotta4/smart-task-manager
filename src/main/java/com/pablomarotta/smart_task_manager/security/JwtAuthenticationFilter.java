package com.pablomarotta.smart_task_manager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsServiceImpl userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                JwtTokenProvider.AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
                AuthenticatedUserPrincipal principal = userDetailsService.loadUserByUsername(claims.username());
                if (matchesCurrentAccount(claims, principal)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
                // An invalid bearer token is treated as unauthenticated; never log credential material.
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesCurrentAccount(
            JwtTokenProvider.AccessTokenClaims claims,
            AuthenticatedUserPrincipal principal
    ) {
        return principal.isEnabled()
                && principal.isAccountNonExpired()
                && principal.isAccountNonLocked()
                && principal.isCredentialsNonExpired()
                && claims.username().equals(principal.getUsername())
                && claims.userId().equals(principal.getUserId())
                && claims.authVersion() == principal.getAuthVersion();
    }
}
