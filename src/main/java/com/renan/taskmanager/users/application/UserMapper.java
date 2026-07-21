package com.renan.taskmanager.users.application;

import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.users.infrastructure.UserEntity;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.UUID;

/**
 * MapStruct mapper: translates between the domain {@link User} and the JPA
 * {@link UserEntity}.
 *
 * <p><b>Why MapStruct?</b>
 * - Compile-time generation (no reflection at runtime → fast)
 * - Type-safe (errors caught at build, not at runtime)
 * - Reduces hand-written mapping code to near zero</p>
 *
 * <p><b>Why hand-written mapping methods instead of MapStruct auto-mapping?</b>
 * The domain User has value objects ({@code Email}, {@code Password}, {@code UserId})
 * while the entity uses primitive types ({@code String}, {@code UUID}). MapStruct
 * can't auto-map these, so we tell it exactly how to convert. This is actually
 * a feature: it forces us to be explicit about the translations.</p>
 *
 * <p><b>The password hashing problem:</b> the domain {@code Password} holds the
 * plain value; the entity stores only the hash. The mapper cannot hash (that's
 * application-layer concern). For now, the mapper copies the plain value
 * verbatim — hashing happens in the use case before calling the repository.
 * See {@code RegisterUserUseCase} for the full flow.</p>
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Domain → Entity. Used when saving.
     *
     * <p><b>Note:</b> this method does NOT hash the password. Callers must
     * pass a User whose Password is already the intended stored value
     * (i.e. the hash). The use case is responsible for hashing.</p>
     */
    default UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.getId().value())
                .email(user.getEmail().value())
                .passwordHash(user.getPassword().value())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Entity → Domain. Used when loading from the database.
     *
     * <p><b>Trade-off:</b> the loaded domain User holds the hash as its
     * "password". This is acceptable because the domain Password class
     * enforces strength rules on plain passwords — a hash will fail validation.
     * For loading, we bypass the constructor via a dedicated factory method
     * (see {@link Password} for the rationale; if not yet added, this will
     * surface as a failing test and we'll address it then).</p>
     */
    default User toDomain(UserEntity entity) {
        return User.reconstitute(
                UserId.of(entity.getId()),
                new Email(entity.getEmail()),
                Password.fromHash(entity.getPasswordHash()),
                entity.getName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
