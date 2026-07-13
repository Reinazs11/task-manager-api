package com.renan.taskmanager.users.domain;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("Should be equal when they share the same UUID")
    void shouldBeEqualWithSameUUID() {
        UUID uuid = UUID.randomUUID();
        UserId a = UserId.of(uuid);
        UserId b = UserId.of(uuid);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
