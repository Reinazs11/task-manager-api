package com.renan.taskmanager.users.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object representing a valid email address.
 *
 * <p><b>Why a value object instead of a String?</b>
 * When you have {@code String email} scattered across the codebase, nothing
 * prevents passing "john" (no @) into a method. With {@code Email}, the object
 * only exists if it's valid — the compiler now protects you. Validation bugs
 * are eliminated at the source.</p>
 *
 * <p><b>Immutability:</b> once created, it never changes. To "change" a user's
 * email, we create a new Email. This is a fundamental property of value objects
 * in DDD.</p>
 *
 * <p><b>Normalization:</b> emails are case-insensitive in practice, so we always
 * store them lowercase.</p>
 *
 * <p><b>Regex trade-off:</b> the pattern below is intentionally simple — it
 * rejects the common invalid cases (missing @, missing domain, spaces) without
 * rejecting valid edge cases. Full RFC 5322 compliance is a rabbit hole not
 * worth it for a portfolio project.</p>
 */
public final class Email {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final String value;

    /**
     * Creates a validated Email.
     *
     * @param value email address (will be normalized to lowercase)
     * @throws IllegalArgumentException if null, blank, or invalid format
     */
    public Email(String value) {
        validate(value);
        this.value = value.toLowerCase();
    }

    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
