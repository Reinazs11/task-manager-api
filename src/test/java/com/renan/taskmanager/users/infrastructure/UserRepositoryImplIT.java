package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.common.TestContainersConfig;
import com.renan.taskmanager.users.application.UserMapperImpl;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link UserRepositoryImpl} against a real PostgreSQL
 * database running in Docker via Testcontainers.
 *
 * <p><b>Why not H2?</b>
 * H2 is an in-memory database with subtly different SQL dialect and type
 * behavior than PostgreSQL. Bugs that only manifest in Postgres (UUID column
 * types, JSONB, case-sensitivity) would be hidden. Testcontainers gives us the
 * real thing.</p>
 *
 * <p><b>Why @DataJpaTest and not @SpringBootTest?</b>
 * {@code @DataJpaTest} is a slice test: it loads only the JPA layer (entities,
 * repositories), not the whole application context. It's faster and more
 * focused. We override the default embedded DB with our Testcontainers
 * PostgreSQL via {@link AutoConfigureTestDatabase}.</p>
 *
 * <p><b>Why {@code @Import(TestContainersConfig.class)} (and not a static
 * {@code @Container} field)?</b>
 * Same rationale as {@link com.renan.taskmanager.common.AbstractIntegrationTest}:
 * the container's lifecycle is bound to Spring's, which avoids
 * connection-refused races when the TestContext is reused. Even though this
 * class runs alone in its own slice context, sharing the same config keeps the
 * project consistent and lets a future class reuse this context.</p>
 */
@DataJpaTest
@Import({UserRepositoryImpl.class, UserMapperImpl.class, TestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryImplIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    @DisplayName("Should persist and retrieve a user by email")
    void shouldPersistAndRetrieveByEmail() {
        // Arrange — use fromHash because we're simulating a stored user
        User user = User.create(
                new Email("renan@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc"),
                "Renan"
        );

        // Act
        userRepository.save(user);
        Optional<User> found = userRepository.findByEmail(new Email("renan@example.com"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getEmail().value()).isEqualTo("renan@example.com");
        assertThat(found.get().getName()).isEqualTo("Renan");
    }

    @Test
    @DisplayName("Should return empty when email is not found")
    void shouldReturnEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail(new Email("nonexistent@example.com"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should detect existing email")
    void shouldDetectExistingEmail() {
        User user = User.create(
                new Email("existing@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        );
        userRepository.save(user);

        boolean exists = userRepository.existsByEmail(new Email("existing@example.com"));

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when email does not exist")
    void shouldReturnFalseWhenEmailDoesNotExist() {
        boolean exists = userRepository.existsByEmail(new Email("ghost@example.com"));

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should preserve timestamps when saving and loading")
    void shouldPreserveTimestamps() {
        User user = User.create(
                new Email("timestamps@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        );
        User saved = userRepository.save(user);

        Optional<User> reloaded = userRepository.findByEmail(new Email("timestamps@example.com"));

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(reloaded.get().getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("Should enforce email uniqueness at the database level")
    void shouldEnforceEmailUniqueness() {
        User user1 = User.create(
                new Email("unique@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        );
        userRepository.save(user1);
        userJpaRepository.flush();  // force the INSERT so the unique index is populated

        // Same email, different ID — should violate the unique constraint on flush
        User user2 = User.create(
                new Email("unique@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            userRepository.save(user2);
            userJpaRepository.flush();  // force the second INSERT to trigger the violation
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
