package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.AuthenticationException;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefreshTokenFamilyReplayIntegrationTest extends PostgresIntegrationTest {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Autowired
    RefreshTokenFamilyReplayIntegrationTest(
            DataSource dataSource,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository
    ) {
        super(dataSource);
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    @Test
    void replayingARotatedAncestorRevokesItsDescendant() {
        User user = saveUser();
        String original = refreshTokenService.issueForUsername(user.getUsername()).value();
        String descendant = refreshTokenService.rotate(original).value();

        assertThatThrownBy(() -> refreshTokenService.rotate(original))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(descendant))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void logoutUsingARotatedAncestorRevokesItsDescendant() {
        User user = saveUser();
        String original = refreshTokenService.issueForUsername(user.getUsername()).value();
        String descendant = refreshTokenService.rotate(original).value();

        refreshTokenService.revoke(original);

        assertThatThrownBy(() -> refreshTokenService.rotate(descendant))
                .isInstanceOf(AuthenticationException.class);
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "family" + suffix.substring(0, 12);
        String email = username + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .emailNormalized(email)
                .password("encoded-password")
                .fullName("Refresh Token Family User")
                .build());
    }
}
