package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.application.UserMapper;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserAlreadyExistsException;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class UserRepositoryImplTest {

    private UserJpaRepository jpaRepository;
    private UserMapper mapper;
    private EntityManager entityManager;
    private UserRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(UserJpaRepository.class);
        mapper = mock(UserMapper.class);
        entityManager = mock(EntityManager.class);
        repository = new UserRepositoryImpl(jpaRepository, mapper, entityManager);
    }

    @Test
    @DisplayName("Should translate only the users email constraint")
    void shouldTranslateEmailConstraint() {
        User user = user("duplicate@example.com");
        UserEntity entity = mock(UserEntity.class);
        when(mapper.toEntity(user)).thenReturn(entity);
        doThrow(constraintViolation("uc_users_email")).when(entityManager).flush();

        assertThatThrownBy(() -> repository.save(user))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("duplicate@example.com");
    }

    @Test
    @DisplayName("Should preserve unrelated integrity violations")
    void shouldPreserveOtherConstraintViolations() {
        User user = user("valid@example.com");
        UserEntity entity = mock(UserEntity.class);
        DataIntegrityViolationException violation = integrityViolation("other_constraint");
        when(mapper.toEntity(user)).thenReturn(entity);
        doThrow(violation).when(entityManager).flush();

        assertThatThrownBy(() -> repository.save(user)).isSameAs(violation);
    }

    private User user(String email) {
        return User.create(
                new Email(email),
                Password.fromHash("$2a$12$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        );
    }

    private DataIntegrityViolationException integrityViolation(String constraint) {
        return new DataIntegrityViolationException(
                "insert failed",
                constraintViolation(constraint)
        );
    }

    private ConstraintViolationException constraintViolation(String constraint) {
        SQLException sqlException = new SQLException("constraint violation");
        return new ConstraintViolationException("insert failed", sqlException, constraint);
    }
}
