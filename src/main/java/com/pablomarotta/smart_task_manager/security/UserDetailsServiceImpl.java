package com.pablomarotta.smart_task_manager.security;

import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthenticatedUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole().name(),
                user.getAuthVersion() == null ? 0 : user.getAuthVersion(),
                Boolean.TRUE.equals(user.getActive())
        );
    }
}
