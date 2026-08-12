package com.pablomarotta.smart_task_manager.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loadsTheCurrentPrincipalOnceAndAuthenticatesOnlyWhenAllIdentityFieldsMatch() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks/alltasks");
        request.addHeader("Authorization", "Bearer access-token");
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                42L,
                "alice",
                "encoded-password",
                "USER",
                3,
                true
        );
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 42L, 3));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(principal);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(principal);
        verify(jwtTokenProvider, times(1)).parseAccessToken("access-token");
        verify(userDetailsService, times(1)).loadUserByUsername("alice");
    }

    @Test
    void rejectsAValidlySignedTokenWhenTheCurrentPrincipalAuthenticationVersionChanged() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks/alltasks");
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 42L, 3));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(new AuthenticatedUserPrincipal(
                42L,
                "alice",
                "encoded-password",
                "USER",
                4,
                true
        ));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, times(1)).loadUserByUsername("alice");
    }

    @Test
    void rejectsAValidlySignedTokenWhenTheCurrentPrincipalHasADifferentUserId() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks/alltasks");
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 42L, 3));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(new AuthenticatedUserPrincipal(
                99L,
                "alice",
                "encoded-password",
                "USER",
                3,
                true
        ));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, times(1)).loadUserByUsername("alice");
    }

    @Test
    void rejectsAValidlySignedTokenWhenTheCurrentPrincipalHasADifferentUsername() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks/alltasks");
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 42L, 3));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(new AuthenticatedUserPrincipal(
                42L,
                "bob",
                "encoded-password",
                "USER",
                3,
                true
        ));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, times(1)).loadUserByUsername("alice");
    }
}
