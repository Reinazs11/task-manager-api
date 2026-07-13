package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BCryptPasswordHasher}.
 *
 * <p>Pure unit test — no Spring context needed. We instantiate the hasher
 * directly. This is fast and isolates the behavior.</p>
 *
 * <p><b>Key BCrypt properties tested:</b>
 * - Same password produces different hashes (salt is random)
 * - Hash is verifiable against the original password
 * - Wrong password does not match</p>
 */
class BCryptPasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new BCryptPasswordHasher();
    }

    @Nested
    @DisplayName("Hashing")
    class Hashing {

        @Test
        @DisplayName("Should produce a non-empty hash")
        void shouldProduceNonEmptyHash() {
            Password password = new Password("Password123");

            String hash = hasher.hash(password);

            assertThat(hash).isNotBlank();
        }

        @Test
        @DisplayName("Should produce a BCrypt-formatted hash")
        void shouldProduceBCryptHash() {
            // BCrypt hashes always start with $2a$, $2b$, or $2y$
            Password password = new Password("Password123");

            String hash = hasher.hash(password);

            assertThat(hash).startsWith("$2a$");
        }

        @Test
        @DisplayName("Should produce different hashes for the same password (salt)")
        void shouldProduceDifferentHashesForSamePassword() {
            // Random salt means two hashes of the same password differ.
            // This is a defining property of BCrypt.
            Password password = new Password("Password123");

            String hash1 = hasher.hash(password);
            String hash2 = hasher.hash(password);

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("Matching")
    class Matching {

        @Test
        @DisplayName("Should match the original password against its hash")
        void shouldMatchOriginalPassword() {
            Password password = new Password("Password123");
            String hash = hasher.hash(password);

            boolean matches = hasher.matches(password, hash);

            assertThat(matches).isTrue();
        }

        @Test
        @DisplayName("Should not match a wrong password")
        void shouldNotMatchWrongPassword() {
            Password original = new Password("Password123");
            String hash = hasher.hash(original);

            Password wrong = new Password("DifferentPassword456");
            boolean matches = hasher.matches(wrong, hash);

            assertThat(matches).isFalse();
        }

        @Test
        @DisplayName("Should return false when the stored hash is null")
        void shouldReturnFalseForNullHash() {
            Password password = new Password("Password123");

            boolean matches = hasher.matches(password, null);

            assertThat(matches).isFalse();
        }

        @Test
        @DisplayName("Should return false when the stored hash is blank")
        void shouldReturnFalseForBlankHash() {
            Password password = new Password("Password123");

            boolean matches = hasher.matches(password, "");

            assertThat(matches).isFalse();
        }
    }
}
