package com.pablomarotta.smart_task_manager.security;

import com.pablomarotta.smart_task_manager.model.Role;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void mapsUserRoleToSpringAuthority() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user("alice", Role.USER, true)));

        AuthenticatedUserPrincipal details = (AuthenticatedUserPrincipal) userDetailsService.loadUserByUsername("alice");

        assertEquals(
                java.util.List.of("ROLE_USER"),
                details.getAuthorities().stream().map(Object::toString).toList()
        );
        assertTrue(details.isEnabled());
        assertEquals(17L, details.getUserId());
        assertEquals(3, details.getAuthVersion());
    }

    @Test
    void mapsAdminRoleAndDisablesInactiveAccount() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(user("admin", Role.ADMIN, false)));

        AuthenticatedUserPrincipal details = (AuthenticatedUserPrincipal) userDetailsService.loadUserByUsername("admin");

        assertEquals(
                java.util.List.of("ROLE_ADMIN"),
                details.getAuthorities().stream().map(Object::toString).toList()
        );
        assertFalse(details.isEnabled());
    }

    private User user(String username, Role role, boolean active) {
        return User.builder()
                .username(username)
                .id(17L)
                .password("encoded")
                .role(role)
                .active(active)
                .authVersion(3)
                .build();
    }
}
