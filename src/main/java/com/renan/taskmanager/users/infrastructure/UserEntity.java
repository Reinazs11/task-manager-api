package com.renan.taskmanager.users.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for User persistence.
 *
 * <p><b>Why a separate UserEntity and not @Entity on the domain User?</b>
 * Keeping JPA annotations off the domain class keeps the domain pure and
 * framework-agnostic. {@code UserEntity} is a persistence concern; the domain
 * {@code User} is a business concern. {@code UserMapper} translates between
 * them (MapStruct).</p>
 *
 * <p><b>Why UUID generation strategy and not IDENTITY?</b>
 * UUIDs are assigned at the domain layer ({@code UserId.generate()}); the
 * database doesn't generate them. We map the domain's UUID directly to the
 * column.</p>
 *
 * <p>Lombok handles the JPA boilerplate (getters/setters/constructors). JPA
 * spec actually <i>requires</i> a no-arg constructor (can be protected), which
 * is why we have {@code @NoArgsConstructor} here.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Stored as BCrypt hash — never plain text. Naming makes intent explicit.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
