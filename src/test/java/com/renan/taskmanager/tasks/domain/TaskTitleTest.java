package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link TaskTitle} value object.
 *
 * <p>A task title must be non-blank and at most 200 characters. These tests
 * document and enforce the validation rules.</p>
 */
class TaskTitleTest {

    @Nested
    @DisplayName("When creating a valid title")
    class ValidCreation {

        @Test
        @DisplayName("Should accept a normal title")
        void shouldAcceptNormalTitle() {
            TaskTitle title = new TaskTitle("Implement login endpoint");
            assertThat(title.value()).isEqualTo("Implement login endpoint");
        }

        @Test
        @DisplayName("Should trim leading and trailing whitespace")
        void shouldTrimWhitespace() {
            TaskTitle title = new TaskTitle("  padded title  ");
            assertThat(title.value()).isEqualTo("padded title");
        }

        @Test
        @DisplayName("Should accept a title at the maximum length (200 chars)")
        void shouldAcceptMaxLength() {
            String exact = "a".repeat(200);
            TaskTitle title = new TaskTitle(exact);
            assertThat(title.value()).hasSize(200);
        }
    }

    @Nested
    @DisplayName("When creating an invalid title")
    class InvalidCreation {

        @Test
        @DisplayName("Should reject null")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new TaskTitle(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject empty string")
        void shouldRejectEmpty() {
            assertThatThrownBy(() -> new TaskTitle(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject blank (whitespace-only) string")
        void shouldRejectBlank() {
            assertThatThrownBy(() -> new TaskTitle("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject title longer than 200 characters")
        void shouldRejectTooLong() {
            String tooLong = "a".repeat(201);
            assertThatThrownBy(() -> new TaskTitle(tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200");
        }
    }

    @Test
    @DisplayName("Two titles with same value should be equal")
    void equality() {
        TaskTitle a = new TaskTitle("Write tests");
        TaskTitle b = new TaskTitle("Write tests");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
