package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link ProjectName} value object.
 *
 * <p>Mirrors {@link TaskTitleTest} for consistency: same validation rules
 * (non-blank, max 200 chars, trimmed).</p>
 */
class ProjectNameTest {

    @Nested
    @DisplayName("When creating a valid name")
    class ValidCreation {

        @Test
        @DisplayName("Should accept a normal name")
        void shouldAcceptNormalName() {
            ProjectName name = new ProjectName("Backend Refactor");
            assertThat(name.value()).isEqualTo("Backend Refactor");
        }

        @Test
        @DisplayName("Should trim leading and trailing whitespace")
        void shouldTrimWhitespace() {
            ProjectName name = new ProjectName("  Padded  ");
            assertThat(name.value()).isEqualTo("Padded");
        }

        @Test
        @DisplayName("Should accept a name at the maximum length (200 chars)")
        void shouldAcceptMaxLength() {
            String exact = "a".repeat(200);
            ProjectName name = new ProjectName(exact);
            assertThat(name.value()).hasSize(200);
        }
    }

    @Nested
    @DisplayName("When creating an invalid name")
    class InvalidCreation {

        @Test
        @DisplayName("Should reject null")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new ProjectName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject empty string")
        void shouldRejectEmpty() {
            assertThatThrownBy(() -> new ProjectName(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject blank (whitespace-only) string")
        void shouldRejectBlank() {
            assertThatThrownBy(() -> new ProjectName("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject name longer than 200 characters")
        void shouldRejectTooLong() {
            String tooLong = "a".repeat(201);
            assertThatThrownBy(() -> new ProjectName(tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200");
        }
    }

    @Test
    @DisplayName("Two names with same value should be equal")
    void equality() {
        ProjectName a = new ProjectName("My Project");
        ProjectName b = new ProjectName("My Project");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
