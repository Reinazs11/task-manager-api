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
}
