package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.PasswordHasher;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginUseCase}.
 *
 * <p>Verifies the orchestration logic:</p>
 * <ul>
 *   <li>unknown email and wrong password produce the SAME exception (anti-enumeration)</li>
 *   <li>happy path issues both access and refresh tokens</li>
 *   <li>token type claim distinguishes access vs refresh</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    private static final String TEST_SECRET =
            "test-secret-key-with-at-least-32-bytes-for-hs256-signing-tests-Ok!";
    private static final long ACCESS_TTL_MS = 60_000L;
    private static final long REFRESH_TTL_MS = 3_600_000L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    // Real JwtService, not mocked: we want to verify token generation end-to-end
    // (the cryptographic signing is fast and deterministic per secret).
    private JwtService jwtService;
    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS);
        useCase = new LoginUseCase(userRepository, passwordHasher, jwtService, ACCESS_TTL_MS, REFRESH_TTL_MS);
    }

    @Nested
    @DisplayName("When credentials are valid")
    class HappyPath {

        @Test
        @DisplayName("Should return tokens with correct shape and TTLs")
        void shouldReturnTokens() {
            // Arrange — a user persisted with a hashed password
            String storedHash = "$2a$10$storedHashValue";
            User persisted = User.create(
                    new Email("renan@example.com"),
                    Password.fromHash(storedHash),
                    "Renan"
            );
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(persisted));
            when(passwordHasher.matches("Password123", storedHash)).thenReturn(true);

            // Act
            TokenResponse response = useCase.execute("renan@example.com", "Password123");

            // Assert — tokens are non-empty, type is Bearer, TTLs match configuration
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TTL_MS / 1000);       // 60s
            assertThat(response.refreshExpiresIn()).isEqualTo(REFRESH_TTL_MS / 1000); // 3600s

            // The two tokens must be different (different type claim, different jti)
            assertThat(response.accessToken()).isNotEqualTo(response.refreshToken());
        }

        @Test
        @DisplayName("Issued access token should validate and carry type=access")
        void accessTokenShouldBeValidAndAccessType() {
            String storedHash = "$2a$10$storedHashValue";
            User persisted = User.create(
                    new Email("renan@example.com"),
                    Password.fromHash(storedHash)
            );
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(persisted));
            when(passwordHasher.matches("Password123", storedHash)).thenReturn(true);

            TokenResponse response = useCase.execute("renan@example.com", "Password123");

            var claims = jwtService.parseAndValidate(response.accessToken());
            assertThat(jwtService.extractTokenType(claims)).isEqualTo(JwtService.TYPE_ACCESS);
            assertThat(jwtService.extractEmail(claims)).isEqualTo("renan@example.com");
        }
    }

    @Nested
    @DisplayName("When credentials are invalid")
    class InvalidCredentials {

        @Test
        @DisplayName("Unknown email should throw InvalidCredentialsException")
        void shouldThrowForUnknownEmail() {
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute("ghost@example.com", "Password123"))
                    .isInstanceOf(InvalidCredentialsException.class);

            // Security: must NEVER invoke the password hasher when the user doesn't exist.
            // This avoids timing-based user enumeration (hasher is slow by design).
            verify(passwordHasher, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Wrong password should throw the SAME exception as unknown email")
        void shouldThrowSameExceptionForWrongPassword() {
            String storedHash = "$2a$10$storedHashValue";
            User persisted = User.create(
                    new Email("renan@example.com"),
                    Password.fromHash(storedHash)
            );
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(persisted));
            when(passwordHasher.matches("WrongPassword", storedHash)).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute("renan@example.com", "WrongPassword"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Both failure cases should produce identical exception messages")
        void bothFailuresShouldProduceIdenticalMessages() {
            // Unknown email
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());
            String msg1 = catchMessage(() -> useCase.execute("ghost@example.com", "anything"));

            // Wrong password
            User persisted = User.create(
                    new Email("renan@example.com"),
                    Password.fromHash("$2a$10$storedHashValue")
            );
            when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(persisted));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);
            String msg2 = catchMessage(() -> useCase.execute("renan@example.com", "WrongPassword"));

            // Identical message => attackers cannot distinguish "user not found" from "wrong password"
            assertThat(msg1).isEqualTo(msg2);
        }
    }

    /** Helper: runs a block expected to throw InvalidCredentialsException, returns its message. */
    private String catchMessage(Runnable block) {
        try {
            block.run();
            throw new AssertionError("Expected InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }
}
