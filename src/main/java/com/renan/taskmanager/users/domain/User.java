package com.renan.taskmanager.users.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * User entity — aggregate root of the users bounded context.
 *
 * <p><b>Entity vs Value Object:</b>
 * Value objects (Email, Password, UserId) are defined by their values —
 * two "x@y.com" emails are "the same thing". Entities have <b>identity</b>
 * (UserId) that persists even when other fields change.</p>
 *
 * <p><b>Factory methods:</b> we use {@link #create(Email, Password)} and
 * {@link #create(Email, Password, String)} instead of a public constructor.
 * This communicates intent ("create new user") and centralizes ID/timestamp
 * generation. {@link #reconstitute(UserId, Email, Password, String, Instant, Instant)}
 * is used to hydrate from the database (ID and timestamps already exist).</p>
 */
public class User {

    private final UserId id;
    private Email email;
    private Password password;
    private String name;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Private constructor — use the factory methods.
     */
    private User(UserId id, Email email, Password password, String name,
                 Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "UserId is required");
        this.email = Objects.requireNonNull(email, "Email is required");
        this.password = Objects.requireNonNull(password, "Password is required");
        this.name = name;  // name is optional
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /**
     * Creates a brand new user (generates new UserId and timestamps).
     */
    public static User create(Email email, Password password) {
        return create(email, password, null);
    }

    /**
     * Creates a brand new user with an optional display name.
     */
    public static User create(Email email, Password password, String name) {
        Instant now = Instant.now();
        return new User(UserId.generate(), email, password, name, now, now);
    }

    /**
     * Reconstitutes a user from persisted data (ID and timestamps already exist).
     *
     * <p>Used by the infrastructure layer when loading from the database.</p>
     */
    public static User reconstitute(UserId id, Email email, Password password,
                                     String name, Instant createdAt, Instant updatedAt) {
        return new User(id, email, password, name, createdAt, updatedAt);
    }

    public void updateName(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void changeEmail(Email newEmail) {
        this.email = Objects.requireNonNull(newEmail, "Email is required");
        this.updatedAt = Instant.now();
    }

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public Password getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Entities are equal by identity (UserId), not by fields.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
