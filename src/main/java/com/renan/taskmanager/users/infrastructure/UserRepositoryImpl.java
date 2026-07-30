package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.application.UserMapper;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserAlreadyExistsException;
import com.renan.taskmanager.users.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String EMAIL_CONSTRAINT = "uc_users_email";

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;
    private final EntityManager entityManager;

    public UserRepositoryImpl(UserJpaRepository jpaRepository, UserMapper mapper,
                              EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        try {
            entityManager.persist(entity);
            entityManager.flush();
            return mapper.toDomain(entity);
        } catch (ConstraintViolationException exception) {
            throw translateConstraint(exception, user.getEmail());
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception, user.getEmail());
        }
    }

    private RuntimeException translateConstraint(ConstraintViolationException exception,
                                                 Email email) {
        if (EMAIL_CONSTRAINT.equals(exception.getConstraintName())) {
            return new UserAlreadyExistsException(email);
        }
        return exception;
    }

    private RuntimeException translateConstraint(DataIntegrityViolationException exception,
                                                 Email email) {
        if (exception.getCause() instanceof ConstraintViolationException violation
                && EMAIL_CONSTRAINT.equals(violation.getConstraintName())) {
            return new UserAlreadyExistsException(email);
        }
        return exception;
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
