package com.renan.taskmanager.users.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Email value object.
 *
 * <p>Covers: valid creation, rejection of invalid inputs, immutability, and
 * equality semantics (two emails with the same value must be equal — a
 * defining property of value objects).</p>
 */
class EmailTest {

    @Nested
    @DisplayName("When creating a valid email")
    class ValidCreation {

        @Test
        @DisplayName("Should accept a standard-formatted email")
        void shouldAcceptValidEmail() {
            Email email = new Email("renan@example.com");

            assertThat(email.value()).isEqualTo("renan@example.com");
        }

        @Test
        @DisplayName("Should accept an email with a subdomain")
        void shouldAcceptSubdomainEmail() {
            Email email = new Email("user@mail.example.com");
            assertThat(email.value()).isEqualTo("user@mail.example.com");
        }

        @Test
        @DisplayName("Should normalize to lowercase")
        void shouldNormalizeToLowercase() {
            // Emails are case-insensitive in practice. The domain enforces this.
            Email email = new Email("Renan@EXAMPLE.com");
            assertThat(email.value()).isEqualTo("renan@example.com");
        }
    }

    @Nested
    @DisplayName("When creating an invalid email")
    class InvalidCreation {

        @Test
        @DisplayName("Should reject an email without @")
        void shouldRejectEmailWithoutAtSign() {
            assertThatThrownBy(() -> new Email("renanexample.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid");
        }

        @Test
        @DisplayName("Should reject an email without a domain")
        void shouldRejectEmailWithoutDomain() {
            assertThatThrownBy(() -> new Email("renan@"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject an email without a local part")
        void shouldRejectEmailWithoutLocalPart() {
            assertThatThrownBy(() -> new Email("@example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject null")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new Email(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject an empty string")
        void shouldRejectEmpty() {
            assertThatThrownBy(() -> new Email(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject an email with spaces")
        void shouldRejectEmailWithSpaces() {
            assertThatThrownBy(() -> new Email("renan @example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality (value object semantics)")
    class Equality {

        @Test
        @DisplayName("Two emails with the same value should be equal")
        void shouldBeEqual() {
            Email a = new Email("renan@example.com");
            Email b = new Email("renan@example.com");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("Emails with different values should not be equal")
        void shouldNotBeEqual() {
            Email a = new Email("a@example.com");
            Email b = new Email("b@example.com");
            assertThat(a).isNotEqualTo(b);
        }
    }
}
