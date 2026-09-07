package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.sentinel.auth.rbac.Role;
import dev.sentinel.auth.rbac.RoleRepository;
import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários (Mockito) de {@link AuthService} — complemento, não substituto, dos testes de
 * integração ponta a ponta já existentes (`AuthController*Test`, `AuthFullFlowTest`). Colaboradores
 * ({@link UserRepository}, {@link RoleRepository}, {@link RefreshTokenRepository},
 * {@link PasswordEncoder}, {@link JwtService}) são todos mockados — sem banco, sem contexto Spring
 * (ticket #16, débito rastreado desde a issue-mãe #4).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Duration REFRESH_TOKEN_TTL_DAYS = Duration.ofDays(7);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                REFRESH_TOKEN_TTL_DAYS.toDays());
    }

    private static User userWithId(UUID id) {
        User user = new User("user@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Nested
    class Register {

        @Test
        void savesUserWithEncodedPasswordAndDefaultRole() {
            RegisterRequest request = new RegisterRequest("new@example.com", "Str0ngP@ssw0rd!");
            Role userRole = new Role("USER");

            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
            when(passwordEncoder.encode(request.password())).thenReturn("encoded-hash");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RegisterResponse response = authService.register(request);

            assertThat(response.email()).isEqualTo("new@example.com");

            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(savedUser.capture());
            assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("encoded-hash");
            assertThat(savedUser.getValue().getRoles()).containsExactly(userRole);
        }

        @Test
        void rejectsRegistrationWhenEmailAlreadyExists() {
            RegisterRequest request = new RegisterRequest("taken@example.com", "Str0ngP@ssw0rd!");
            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(userRepository, never()).saveAndFlush(any());
            verifyNoInteractions(roleRepository);
        }

        @Test
        void rejectsRegistrationWhenDefaultRoleIsNotSeeded() {
            RegisterRequest request = new RegisterRequest("new@example.com", "Str0ngP@ssw0rd!");
            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.register(request)).isInstanceOf(IllegalStateException.class);

            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void translatesConcurrentDuplicateEmailIntoEmailAlreadyRegistered() {
            RegisterRequest request = new RegisterRequest("race@example.com", "Str0ngP@ssw0rd!");
            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(new Role("USER")));
            when(passwordEncoder.encode(request.password())).thenReturn("encoded-hash");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyRegisteredException.class);
        }
    }

    @Nested
    class Login {

        @Test
        void issuesTokenPairForValidCredentials() {
            LoginRequest request = new LoginRequest("user@example.com", "Str0ngP@ssw0rd!");
            User user = userWithId(UUID.randomUUID());
            user.addRole(new Role("USER"));

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
            when(jwtService.generateAccessToken(eq(user.getId()), eq(Set.of("USER")))).thenReturn("access-token");
            when(jwtService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

            LoginResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(900);
            assertThat(response.refreshToken()).isNotBlank();

            ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(savedToken.capture());
            assertThat(savedToken.getValue().getUser()).isEqualTo(user);
        }

        @Test
        void rejectsLoginWhenEmailDoesNotExist() {
            LoginRequest request = new LoginRequest("missing@example.com", "Str0ngP@ssw0rd!");
            when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(passwordEncoder, jwtService, refreshTokenRepository);
        }

        @Test
        void rejectsLoginForLockedAccount() {
            LoginRequest request = new LoginRequest("user@example.com", "Str0ngP@ssw0rd!");
            User user = userWithId(UUID.randomUUID());
            user.setLocked(true);
            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(passwordEncoder, jwtService, refreshTokenRepository);
        }

        @Test
        void rejectsLoginForDisabledAccount() {
            LoginRequest request = new LoginRequest("user@example.com", "Str0ngP@ssw0rd!");
            User user = userWithId(UUID.randomUUID());
            user.setEnabled(false);
            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(passwordEncoder, jwtService, refreshTokenRepository);
        }

        @Test
        void rejectsLoginForWrongPassword() {
            LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
            User user = userWithId(UUID.randomUUID());
            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(jwtService, refreshTokenRepository);
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesRefreshTokenAndIssuesNewPair() {
            User user = userWithId(UUID.randomUUID());
            user.addRole(new Role("USER"));
            RefreshToken existingToken = new RefreshToken(user, "irrelevant-hash", Instant.now().plusSeconds(3600));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
            when(jwtService.generateAccessToken(eq(user.getId()), eq(Set.of("USER")))).thenReturn("new-access-token");
            when(jwtService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

            LoginResponse response = authService.refresh("raw-refresh-token");

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            verify(refreshTokenRepository).delete(existingToken);

            ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(savedToken.capture());
            assertThat(savedToken.getValue().getUser()).isEqualTo(user);
        }

        @Test
        void rejectsNullRefreshToken() {
            assertThatThrownBy(() -> authService.refresh(null)).isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(refreshTokenRepository, jwtService);
        }

        @Test
        void rejectsBlankRefreshToken() {
            assertThatThrownBy(() -> authService.refresh("   ")).isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(refreshTokenRepository, jwtService);
        }

        @Test
        void rejectsUnknownRefreshToken() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("raw-refresh-token"))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        void rejectsExpiredRefreshToken() {
            User user = userWithId(UUID.randomUUID());
            RefreshToken expiredToken = new RefreshToken(user, "irrelevant-hash", Instant.now().minusSeconds(1));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refresh("raw-refresh-token"))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verify(refreshTokenRepository, never()).delete(any());
        }
    }

    @Nested
    class Logout {

        @Test
        void deletesRefreshTokenOwnedByAuthenticatedUser() {
            UUID userId = UUID.randomUUID();
            User user = userWithId(userId);
            RefreshToken token = new RefreshToken(user, "irrelevant-hash", Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            authService.logout(userId, "raw-refresh-token");

            verify(refreshTokenRepository).delete(token);
        }

        @Test
        void rejectsNullRefreshToken() {
            UUID userId = UUID.randomUUID();

            assertThatThrownBy(() -> authService.logout(userId, null))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        void rejectsBlankRefreshToken() {
            UUID userId = UUID.randomUUID();

            assertThatThrownBy(() -> authService.logout(userId, "   "))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        void rejectsUnknownRefreshToken() {
            UUID userId = UUID.randomUUID();
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.logout(userId, "raw-refresh-token"))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        void rejectsRefreshTokenBelongingToAnotherUser() {
            UUID authenticatedUserId = UUID.randomUUID();
            User tokenOwner = userWithId(UUID.randomUUID());
            RefreshToken token = new RefreshToken(tokenOwner, "irrelevant-hash", Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> authService.logout(authenticatedUserId, "raw-refresh-token"))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verify(refreshTokenRepository, never()).delete(any());
        }
    }
}
