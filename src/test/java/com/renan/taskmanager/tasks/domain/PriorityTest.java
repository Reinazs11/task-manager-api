package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link Priority} enum.
 *
 * <p>Priority is a simple enum with no transition rules (unlike {@link TaskStatus}).
 * These tests verify the default value and the enum shape.</p>
 */
class PriorityTest {

    @Test
    @DisplayName("Should have exactly three levels: LOW, MEDIUM, HIGH")
    void shouldHaveThreeLevels() {
        assertThat(Priority.values())
                .containsExactly(Priority.LOW, Priority.MEDIUM, Priority.HIGH);
    }

    @Test
    @DisplayName("MEDIUM should be the default")
    void defaultShouldBeMedium() {
        assertThat(Priority.DEFAULT).isEqualTo(Priority.MEDIUM);
    }
}
