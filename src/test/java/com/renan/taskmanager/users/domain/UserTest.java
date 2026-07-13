package com.renan.taskmanager.users.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the User entity.
 *
 * <p>Unlike value objects (Email, Password, UserId), the User entity HAS its
 * own identity (its UserId) and can change over time.</p>
 *
 * <p>We build User via static factory methods instead of a direct public
 * constructor — this forces passing the correct value objects and makes
 * intent explicit (User.create vs new User).</p>
 */
class UserTest {

    private static final Email VALID_EMAIL = new Email("renan@example.com");
    private static final Password VALID_PASSWORD = new Password("Password123");

    @Nested
    @DisplayName("When creating a user")
    class Creation {

        @Test
        @DisplayName("Should create with email, password, and a generated UserId")
        void shouldCreateCompleteUser() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);

            assertThat(user.getId()).isNotNull();
            assertThat(user.getEmail()).isEqualTo(VALID_EMAIL);
            assertThat(user.getPassword()).isEqualTo(VALID_PASSWORD);
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        }

        @Test
        @DisplayName("Should accept an optional name")
        void shouldAcceptOptionalName() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD, "Renan Silva");

            assertThat(user.getName()).contains("Renan Silva");
        }

        @Test
        @DisplayName("Should work without a name (nullable)")
        void shouldWorkWithoutName() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);

            assertThat(user.getName()).isNull();
        }

        @Test
        @DisplayName("Should generate different IDs for different users")
        void idsShouldBeDifferent() {
            User u1 = User.create(VALID_EMAIL, VALID_PASSWORD);
            User u2 = User.create(new Email("other@example.com"), VALID_PASSWORD);

            assertThat(u1.getId()).isNotEqualTo(u2.getId());
        }
    }

    @Nested
    @DisplayName("When modifying a user")
    class Modification {

        @Test
        @DisplayName("Should allow updating the name")
        void shouldAllowUpdatingName() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);

            user.updateName("New Name");

            assertThat(user.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("Should update updatedAt when the name changes")
        void shouldUpdateTimestampWhenNameChanges() throws InterruptedException {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);
            Instant originalUpdatedAt = user.getUpdatedAt();

            Thread.sleep(10);  // ensure clock advances
            user.updateName("New Name");

            assertThat(user.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("Should allow changing the email")
        void shouldAllowChangingEmail() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);

            user.changeEmail(new Email("new@example.com"));

            assertThat(user.getEmail()).isEqualTo(new Email("new@example.com"));
        }

        @Test
        @DisplayName("Should reject an invalid email when changing")
        void shouldRejectInvalidEmailWhenChanging() {
            User user = User.create(VALID_EMAIL, VALID_PASSWORD);

            assertThatThrownBy(() -> user.changeEmail(new Email("invalid")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality (entity identity)")
    class Equality {

        @Test
        @DisplayName("Same ID = same entity, even if other fields differ")
        void sameIdentity() {
            User u1 = User.create(VALID_EMAIL, VALID_PASSWORD);
            // Simulate the same entity (same ID) with different data
            User u2 = User.reconstitute(
                    u1.getId(),
                    new Email("other@example.com"),
                    VALID_PASSWORD,
                    null,
                    u1.getCreatedAt(),
                    u1.getUpdatedAt()
            );

            // Entities are equal by ID, not by fields.
            assertThat(u1).isEqualTo(u2);
        }
    }
}
