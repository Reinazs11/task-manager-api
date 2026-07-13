package com.renan.taskmanager.users.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 *
 * <p>Provides standard CRUD via {@link JpaRepository} plus custom derived
 * queries. This is an infrastructure-layer interface — the domain never
 * references it directly. {@link UserRepositoryImpl} adapts it to the domain's
 * {@code UserRepository} port.</p>
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Derived query: Spring Data generates the implementation from the method name.
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Derived query: returns true if any user has this email.
     */
    boolean existsByEmail(String email);
}
