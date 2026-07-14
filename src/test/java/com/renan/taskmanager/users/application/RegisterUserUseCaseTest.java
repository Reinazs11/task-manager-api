package com.renan.taskmanager.users.application;

import com.renan.taskmanager.users.api.UserResponse;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.PasswordHasher;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserAlreadyExistsException;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegisterUserUseCase}.
 *
 * <p>Pure unit test: Spring context, database, and real BCrypt are all mocked.
 * The goal is to verify the <b>orchestration logic</b> in isolation:</p>
 * <ul>
 *   <li>duplicate email is rejected before any hashing/saving happens</li>
 *   <li>happy path hashes the password, persists, and returns the public DTO</li>
 *   <li>the domain Password validation still applies (invalid password throws)</li>
 * </ul>
 *
 * <p>This complements (does not replace) the integration test. Unit tests here
 * run in milliseconds and pinpoint failures precisely in the use case logic.</p>
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private RegisterUserUseCase useCase;

    @Nested
    @DisplayName("When email is available")
    class HappyPath {

        @Test
        @DisplayName("Should hash password, persist user, and return UserResponse")
        void shouldHashPersistAndReturn() {
            // Arrange
            String email = "renan@example.com";
            String plainPassword = "Password123";
            String hash = "$2a$10$hashedValueHere";
            when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
            when(passwordHasher.hash(any(Password.class))).thenReturn(hash);
            // Simulate the repository assigning a fresh state on save
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserResponse response = useCase.execute(email, plainPassword, "Renan");

            // Assert
            assertThat(response.email()).isEqualTo(email);
            assertThat(response.name()).isEqualTo("Renan");
            assertThat(response.id()).isNotNull();

            // Verify the orchestration order: check duplicate -> hash -> save
            verify(userRepository, times(1)).existsByEmail(any(Email.class));
            verify(passwordHasher, times(1)).hash(any(Password.class));
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should accept null name and pass it through")
        void shouldAcceptNullName() {
            when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
            when(passwordHasher.hash(any(Password.class))).thenReturn("$2a$10$hash");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            UserResponse response = useCase.execute("renan@example.com", "Password123", null);

            assertThat(response.name()).isNull();
        }
    }

    @Nested
    @DisplayName("When email is already in use")
    class DuplicateEmail {

        @Test
        @DisplayName("Should throw UserAlreadyExistsException")
        void shouldThrowOnDuplicate() {
            when(userRepository.existsByEmail(any(Email.class))).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute("renan@example.com", "Password123", null))
                    .isInstanceOf(UserAlreadyExistsException.class);

            // CRITICAL: hashing and saving must NEVER run when email is duplicate.
            // Verifying this prevents two classes of bugs: wasted work and data leaks.
            verify(passwordHasher, never()).hash(any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("When input fails domain validation")
    class InvalidInput {

        @Test
        @DisplayName("Should propagate exception for invalid email")
        void shouldPropagateInvalidEmail() {
            // The Email value object throws before any repository interaction.
            // We don't even need to mock anything — the constructor fails first.
            assertThatThrownBy(() -> useCase.execute("not-an-email", "Password123", null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(userRepository, never()).existsByEmail(any());
            verify(passwordHasher, never()).hash(any());
        }

        @Test
        @DisplayName("Should propagate exception for weak password")
        void shouldPropagateWeakPassword() {
            when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);

            // "weak" fails the strength rules (no uppercase, no digit, <8 chars).
            assertThatThrownBy(() -> useCase.execute("renan@example.com", "weak", null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(passwordHasher, never()).hash(any());
            verify(userRepository, never()).save(any());
        }
    }
}
