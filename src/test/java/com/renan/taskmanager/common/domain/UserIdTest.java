package com.renan.taskmanager.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the UserId value object.
 *
 * <p>UserId encapsulates the user's unique identifier. Uses UUID by default
 * (avoids exposing sequential IDs, safer against enumeration).</p>
 */
class UserIdTest {

    @Test
    @DisplayName("Should generate a new random UserId when created without arguments")
    void shouldGenerateNewRandomId() {
        UserId id1 = UserId.generate();
        UserId id2 = UserId.generate();

        assertThat(id1.value()).isNotNull();
        assertThat(id2.value()).isNotNull();
        // Collision probability for UUIDv4 is negligible.
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Should create a UserId from an existing UUID")
    void shouldCreateFromExistingUUID() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UserId id = UserId.of(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    /**
     * Equality semantics: a value object must be interchangeable with another
     * holding the same value. Covers all branches of {@code equals} so a
     * regression in any of them is caught (PIT mutation testing).
     */
    @Nested
    @DisplayName("Equality contract")
    class Equality {

        @Test
        @DisplayName("Should be equal when they share the same UUID")
        void shouldBeEqualWithSameUUID() {
            UUID uuid = UUID.randomUUID();
            UserId a = UserId.of(uuid);
            UserId b = UserId.of(uuid);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("Should be equal to itself (reflexive)")
        void shouldBeEqualToItself() {
            UserId id = UserId.generate();

            assertThat(id).isEqualTo(id);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            UserId id = UserId.generate();

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should not be equal to a different type")
        void shouldNotBeEqualToDifferentType() {
            UserId id = UserId.generate();

            assertThat(id).isNotEqualTo("not-a-userid");
        }

        @Test
        @DisplayName("Should not be equal to a UserId with a different UUID")
        void shouldNotBeEqualToDifferentUUID() {
            UserId a = UserId.generate();
            UserId b = UserId.generate();

            assertThat(a).isNotEqualTo(b);
        }
    }
}
