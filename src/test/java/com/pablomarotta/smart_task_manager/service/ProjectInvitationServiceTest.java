package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.InvitationProperties;
import com.pablomarotta.smart_task_manager.dto.CreateProjectInvitationRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectInvitationAcceptanceResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectInvitationResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectInvitationErrorCode;
import com.pablomarotta.smart_task_manager.exception.ProjectInvitationException;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectInvitation;
import com.pablomarotta.smart_task_manager.model.ProjectInvitationState;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectInvitationRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.InvitationTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T18:00:00Z");
    private static final Long PROJECT_ID = 20L;
    private static final String OWNER_USERNAME = "owner";
    private static final String TARGET_EMAIL = "member@example.com";
    private static final String TOKEN = "signed-invitation-token";
    private static final String TOKEN_HASH = "a".repeat(64);

    @Mock
    private InvitationTokenCodec invitationTokenCodec;
    @Mock
    private ProjectInvitationRepository invitationRepository;
    @Mock
    private ProjectMembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailOutboxRepository emailOutboxRepository;
    @Mock
    private ProjectAccessPolicy accessPolicy;

    private ProjectInvitationService service;
    private Project project;
    private User owner;
    private User target;

    @BeforeEach
    void setUp() {
        InvitationProperties invitationProperties = new InvitationProperties();
        invitationProperties.setExpiration(Duration.ofDays(7));
        invitationProperties.setLinkBaseUrl("https://tasks.example.test");
        service = new ProjectInvitationService(
                invitationTokenCodec,
                invitationRepository,
                membershipRepository,
                userRepository,
                emailOutboxRepository,
                accessPolicy,
                Clock.fixed(NOW, ZoneOffset.UTC),
                invitationProperties
        );
        owner = user(1L, OWNER_USERNAME, "owner@example.com", NOW.minusSeconds(1));
        target = user(2L, "target", TARGET_EMAIL, NOW.minusSeconds(1));
        project = Project.builder().id(PROJECT_ID).name("Release").owner(owner).build();
    }

    @Test
    void ownerCreatesMemberInvitationWithOnlyTokenHashAndFragmentUrl() {
        when(accessPolicy.requireManager(PROJECT_ID, OWNER_USERNAME)).thenReturn(membership(owner, ProjectRole.OWNER));
        when(userRepository.findActiveForUpdateByUsername(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findByEmailNormalized(TARGET_EMAIL)).thenReturn(Optional.empty());
        when(invitationRepository.findPendingForUpdateByProjectIdAndEmailNormalized(PROJECT_ID, TARGET_EMAIL))
                .thenReturn(Optional.empty());
        when(invitationTokenCodec.encode(any(), eq(1), eq(NOW), eq(NOW.plus(Duration.ofDays(7)))))
                .thenReturn(TOKEN);
        when(invitationTokenCodec.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(invitationRepository.save(any(ProjectInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectInvitationResponse response = service.createInvitation(
                PROJECT_ID,
                new CreateProjectInvitationRequest(" MEMBER@EXAMPLE.COM ", ProjectRole.MEMBER),
                OWNER_USERNAME
        );

        ArgumentCaptor<ProjectInvitation> invitationCaptor = ArgumentCaptor.forClass(ProjectInvitation.class);
        ArgumentCaptor<EmailOutbox> outboxCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(invitationRepository).save(invitationCaptor.capture());
        verify(emailOutboxRepository).save(outboxCaptor.capture());
        assertThat(invitationCaptor.getValue().getEmailNormalized()).isEqualTo(TARGET_EMAIL);
        assertThat(invitationCaptor.getValue().getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(invitationCaptor.getValue().getTokenHash()).isEqualTo(TOKEN_HASH).doesNotContain(TOKEN);
        assertThat(response.email()).isEqualTo(TARGET_EMAIL);
        assertThat(response.inviteUrl()).isEqualTo("https://tasks.example.test/invite#token=" + TOKEN);
        assertThat(outboxCaptor.getValue().getRecipientEmailNormalized()).isEqualTo(TARGET_EMAIL);
        assertThat(outboxCaptor.getValue().getProjectInvitation()).isSameAs(invitationCaptor.getValue());
    }

    @Test
    void managerCannotInviteAManager() {
        when(accessPolicy.requireManager(PROJECT_ID, OWNER_USERNAME)).thenReturn(membership(owner, ProjectRole.MANAGER));
        when(userRepository.findActiveForUpdateByUsername(OWNER_USERNAME)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.createInvitation(
                PROJECT_ID,
                new CreateProjectInvitationRequest(TARGET_EMAIL, ProjectRole.MANAGER),
                OWNER_USERNAME
        )).isInstanceOf(AccessDeniedException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void rejectsInvitationForAnExistingMemberBeforeIssuingAToken() {
        when(accessPolicy.requireManager(PROJECT_ID, OWNER_USERNAME)).thenReturn(membership(owner, ProjectRole.OWNER));
        when(userRepository.findActiveForUpdateByUsername(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findByEmailNormalized(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.existsByProjectIdAndUserId(PROJECT_ID, target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createInvitation(
                PROJECT_ID,
                new CreateProjectInvitationRequest(TARGET_EMAIL, ProjectRole.MEMBER),
                OWNER_USERNAME
        )).isInstanceOf(ProjectInvitationException.class)
                .extracting(error -> ((ProjectInvitationException) error).getCode())
                .isEqualTo(ProjectInvitationErrorCode.INVITATION_ALREADY_MEMBER);

        verify(invitationTokenCodec, never()).encode(any(), any(Integer.class), any(), any());
    }

    @Test
    void acceptUsesPersistedRoleInsteadOfForgedClientFieldsAndIsIdempotent() {
        UUID invitationId = UUID.randomUUID();
        ProjectInvitation invitation = pendingInvitation(invitationId, ProjectRole.MANAGER);
        when(invitationTokenCodec.decodeForClaim(TOKEN)).thenReturn(decoded(invitationId));
        when(invitationTokenCodec.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(userRepository.findActiveForUpdateByUsername("target")).thenReturn(Optional.of(target));
        when(invitationRepository.findForUpdateByIdAndTokenHash(invitationId, TOKEN_HASH)).thenReturn(Optional.of(invitation));
        when(membershipRepository.findByProjectIdAndUserId(PROJECT_ID, target.getId())).thenReturn(Optional.empty());
        when(membershipRepository.save(any(ProjectMembership.class))).thenAnswer(invocation -> {
            ProjectMembership membership = invocation.getArgument(0);
            membership.setId(77L);
            membership.setJoinedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
            return membership;
        });

        ProjectInvitationAcceptanceResponse first = service.acceptInvitation(TOKEN, "target");
        ProjectInvitationAcceptanceResponse retry = service.acceptInvitation(TOKEN, "target");

        assertThat(first.projectId()).isEqualTo(PROJECT_ID);
        assertThat(first.member().getRole()).isEqualTo(ProjectRole.MANAGER);
        assertThat(retry.member().getMembershipId()).isEqualTo(77L);
        assertThat(invitation.getState()).isEqualTo(ProjectInvitationState.ACCEPTED);
        verify(membershipRepository).save(any(ProjectMembership.class));
        verify(emailOutboxRepository).save(any(EmailOutbox.class));
    }

    @Test
    void acceptsOnlyForAMatchingVerifiedEmailAndExpiresTheInvitation() {
        UUID invitationId = UUID.randomUUID();
        ProjectInvitation invitation = pendingInvitation(invitationId, ProjectRole.MEMBER);
        target.setVerifiedAt(null);
        when(invitationTokenCodec.decodeForClaim(TOKEN)).thenReturn(decoded(invitationId));
        when(invitationTokenCodec.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(userRepository.findActiveForUpdateByUsername("target")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.acceptInvitation(TOKEN, "target"))
                .isInstanceOf(ProjectInvitationException.class)
                .extracting(error -> ((ProjectInvitationException) error).getCode())
                .isEqualTo(ProjectInvitationErrorCode.INVITATION_EMAIL_NOT_VERIFIED);

        target.setVerifiedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        invitation.setExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(invitationRepository.findForUpdateByIdAndTokenHash(invitationId, TOKEN_HASH)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(TOKEN, "target"))
                .isInstanceOf(ProjectInvitationException.class)
                .extracting(error -> ((ProjectInvitationException) error).getCode())
                .isEqualTo(ProjectInvitationErrorCode.INVITATION_EXPIRED);
        assertThat(invitation.getState()).isEqualTo(ProjectInvitationState.EXPIRED);
    }

    @Test
    void revokeAndDeclineOnlyTransitionPendingInvitations() {
        UUID invitationId = UUID.randomUUID();
        ProjectInvitation invitation = pendingInvitation(invitationId, ProjectRole.MEMBER);
        when(accessPolicy.requireManager(PROJECT_ID, OWNER_USERNAME)).thenReturn(membership(owner, ProjectRole.OWNER));
        when(userRepository.findActiveForUpdateByUsername(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(invitationRepository.findForUpdateByIdAndProjectId(invitationId, PROJECT_ID)).thenReturn(Optional.of(invitation));

        service.revokeInvitation(PROJECT_ID, invitationId, OWNER_USERNAME);

        assertThat(invitation.getState()).isEqualTo(ProjectInvitationState.REVOKED);
        invitation.setState(ProjectInvitationState.PENDING);
        invitation.setRevokedAt(null);
        when(invitationTokenCodec.decodeForClaim(TOKEN)).thenReturn(decoded(invitationId));
        when(invitationTokenCodec.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(userRepository.findActiveForUpdateByUsername("target")).thenReturn(Optional.of(target));
        when(invitationRepository.findForUpdateByIdAndTokenHash(invitationId, TOKEN_HASH)).thenReturn(Optional.of(invitation));

        service.declineInvitation(TOKEN, "target");

        assertThat(invitation.getState()).isEqualTo(ProjectInvitationState.DECLINED);
    }

    private User user(Long id, String username, String email, Instant verifiedAt) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .emailNormalized(email)
                .fullName(username)
                .active(true)
                .verifiedAt(verifiedAt == null ? null : LocalDateTime.ofInstant(verifiedAt, ZoneOffset.UTC))
                .build();
    }

    private ProjectMembership membership(User user, ProjectRole role) {
        return ProjectMembership.builder().id(31L).project(project).user(user).role(role).build();
    }

    private ProjectInvitation pendingInvitation(UUID invitationId, ProjectRole role) {
        return ProjectInvitation.builder()
                .id(invitationId)
                .project(project)
                .inviter(owner)
                .emailNormalized(TARGET_EMAIL)
                .role(role)
                .state(ProjectInvitationState.PENDING)
                .tokenHash(TOKEN_HASH)
                .tokenVersion(1)
                .issuedAt(LocalDateTime.ofInstant(NOW.minusSeconds(30), ZoneOffset.UTC))
                .expiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC))
                .build();
    }

    private InvitationTokenCodec.DecodedInvitationToken decoded(UUID invitationId) {
        return new InvitationTokenCodec.DecodedInvitationToken(
                invitationId,
                1,
                NOW.minusSeconds(30),
                NOW.plusSeconds(300),
                invitationId.toString()
        );
    }
}
