package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.application.UserMapper;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter: implements the domain {@link UserRepository} port using JPA.
 *
 * <p>This is the bridge between the pure domain and the persistence layer.
 * The domain doesn't know about JPA; this class does the translation via
 * {@link UserMapper}.</p>
 *
 * <p><b>Why @Repository and not @Component?</b>
 * {@code @Repository} is a specialization that also enables automatic
 * exception translation (JPA exceptions → Spring's DataAccessException
 * hierarchy).</p>
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    public UserRepositoryImpl(UserJpaRepository jpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }
}
