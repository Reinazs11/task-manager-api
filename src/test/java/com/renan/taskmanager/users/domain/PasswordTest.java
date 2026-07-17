package com.renan.taskmanager.users.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Password value object.
 *
 * <p>Password holds the plain password ONLY for strength-rule validation.
 * Hashing is the infrastructure layer's responsibility (BCrypt), not the
 * domain's. See {@link Password} javadoc for the full rationale.</p>
 *
 * <p>Strength rules (minimum reasonable for a portfolio):
 * - 8+ characters
 * - 1 uppercase letter
 * - 1 lowercase letter
 * - 1 digit</p>
 */
class PasswordTest {

    @Nested
    @DisplayName("When creating a valid password")
    class ValidCreation {

        @Test
        @DisplayName("Should accept a password that meets all criteria")
        void shouldAcceptStrongPassword() {
            Password password = new Password("Password123");

            assertThat(password.value()).isEqualTo("Password123");
        }

        @Test
        @DisplayName("Should accept a password with special characters")
        void shouldAcceptPasswordWithSpecialChars() {
            Password password = new Password("Password@123!");

            assertThat(password.value()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("When reconstituting from a stored hash")
    class FromHash {

        @Test
        @DisplayName("Should bypass strength validation for a BCrypt hash")
        void shouldBypassValidationForHash() {
            // A BCrypt hash doesn't satisfy the plain-password strength rules,
            // so fromHash must skip validation.
            String bcryptHash = "$2a$10$abcdefghijklmnopqrstuv";
            Password password = Password.fromHash(bcryptHash);

            assertThat(password.value()).isEqualTo(bcryptHash);
        }

        @Test
        @DisplayName("Should reject null hash")
        void shouldRejectNullHash() {
            assertThatThrownBy(() -> Password.fromHash(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject blank hash")
        void shouldRejectBlankHash() {
            assertThatThrownBy(() -> Password.fromHash("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("When creating a password that violates strength rules")
    class InvalidCreation {

        @Test
        @DisplayName("Should reject a password shorter than 8 characters")
        void shouldRejectShortPassword() {
            assertThatThrownBy(() -> new Password("Ab1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8");
        }

        @Test
        @DisplayName("Should accept a password with EXACTLY 8 characters (boundary)")
        void shouldAcceptExactBoundaryLength() {
            // 8 chars, 1 upper, 1 lower, 1 digit — the minimum that passes.
            // This kills the PIT "changed conditional boundary" mutation on
            // validate() (i.e. catches a future regression turning < into <=).
            Password password = new Password("Abcdefg1");

            assertThat(password.value()).isEqualTo("Abcdefg1");
        }

        @Test
        @DisplayName("Should reject a password with 7 characters (just below boundary)")
        void shouldRejectJustBelowBoundary() {
            // 7 chars: one below the minimum. Distinct from the short-password
            // case above so the boundary is covered from both sides.
            assertThatThrownBy(() -> new Password("Abcdef1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8");
        }

        @Test
        @DisplayName("Should reject a password without an uppercase letter")
        void shouldRejectPasswordWithoutUppercase() {
            assertThatThrownBy(() -> new Password("password123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("uppercase");
        }

        @Test
        @DisplayName("Should reject a password without a lowercase letter")
        void shouldRejectPasswordWithoutLowercase() {
            assertThatThrownBy(() -> new Password("PASSWORD123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lowercase");
        }

        @Test
        @DisplayName("Should reject a password without a digit")
        void shouldRejectPasswordWithoutDigit() {
            assertThatThrownBy(() -> new Password("StrongPassword"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("digit");
        }

        @Test
        @DisplayName("Should reject null")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new Password(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality (value object semantics)")
    class Equality {

        @Test
        @DisplayName("Two passwords with the same value should be equal")
        void shouldBeEqual() {
            Password a = new Password("Password123");
            Password b = new Password("Password123");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("Should be equal to itself (reflexive)")
        void shouldBeEqualToItself() {
            Password password = new Password("Password123");

            assertThat(password).isEqualTo(password);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            Password password = new Password("Password123");

            assertThat(password).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should not be equal to a different type")
        void shouldNotBeEqualToDifferentType() {
            Password password = new Password("Password123");

            assertThat(password).isNotEqualTo("Password123");
        }

        @Test
        @DisplayName("Should not be equal to a different password")
        void shouldNotBeEqualToDifferentPassword() {
            Password a = new Password("Password123");
            Password b = new Password("DifferentPass1");

            assertThat(a).isNotEqualTo(b);
        }
    }
}
