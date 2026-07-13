package com.renan.taskmanager.users.domain;

import java.util.Objects;

/**
 * Value Object representing a password that meets minimum strength requirements.
 *
 * <p><b>Domain responsibility:</b> validate that the password meets the
 * strength rules (length, uppercase, lowercase, digit).</p>
 *
 * <p><b>NOT the domain's responsibility:</b> hashing. Hashing is infrastructure
 * detail (BCrypt, Argon2) and lives in {@code PasswordHasher} at the
 * application/infrastructure layer. This keeps the domain testable without
 * depending on crypto libraries.</p>
 *
 * <p><b>Strength rules (minimum acceptable for a portfolio):</p>
 * <ul>
 *   <li>8+ characters</li>
 *   <li>1 uppercase letter</li>
 *   <li>1 lowercase letter</li>
 *   <li>1 digit</li>
 * </ul>
 *
 * <p><b>Known limitation:</b> the plain password is held in a String, which is
 * interned by the JVM and cannot be explicitly cleared. For production-grade
 * security we'd use {@code char[]} and zero it after use. Documented here as a
 * conscious trade-off: portfolio simplicity over production hardening.</p>
 */
public final class Password {

    private static final int MIN_LENGTH = 8;

    private final String value;

    /**
     * Creates a validated Password.
     *
     * @param value plain password (will be validated, NOT hashed)
     * @throws IllegalArgumentException if null or violates strength rules
     */
    public Password(String value) {
        validate(value);
        this.value = value;
    }

    /**
     * Factory that bypasses strength validation. Used ONLY when reconstituting
     * a User from the database, where the stored value is already a BCrypt hash
     * (which would fail the plain-password strength rules).
     *
     * <p><b>Why is this package-private and named explicitly?</b>
     * Making it {@code public} would invite misuse (callers skipping validation
     * for plain passwords). The explicit name {@code fromHash} documents intent
     * and surfaces in code review if used incorrectly.</p>
     */
    public static Password fromHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null or blank");
        }
        return new Password(hash, true);
    }

    /**
     * Internal constructor with a validation-bypass flag.
     * The boolean is a guard to avoid ambiguity with the public constructor.
     */
    private Password(String value, boolean skipValidation) {
        if (value == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (!skipValidation) {
            validate(value);
        }
        this.value = value;
    }

    private static void validate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (!hasUppercase(value)) {
            throw new IllegalArgumentException(
                    "Password must contain at least 1 uppercase letter");
        }
        if (!hasLowercase(value)) {
            throw new IllegalArgumentException(
                    "Password must contain at least 1 lowercase letter");
        }
        if (!hasDigit(value)) {
            throw new IllegalArgumentException(
                    "Password must contain at least 1 digit");
        }
    }

    private static boolean hasUppercase(String s) {
        return s.chars().anyMatch(c -> Character.isUpperCase((char) c));
    }

    private static boolean hasLowercase(String s) {
        return s.chars().anyMatch(c -> Character.isLowerCase((char) c));
    }

    private static boolean hasDigit(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Password password = (Password) o;
        return Objects.equals(value, password.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
