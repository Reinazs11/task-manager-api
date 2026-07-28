package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.infrastructure.RevokedRefreshTokenEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper: translates between the domain {@link RevokedRefreshToken}
 * and the JPA {@link RevokedRefreshTokenEntity}.
 *
 * <p>Same rationale as {@link UserMapper}: keep JPA out of the domain, be
 * explicit about value-object ↔ primitive translation, and rely on compile-time
 * code generation. The mapping is trivial (every field is already a primitive
 * or {@link java.util.UUID}), so the methods are hand-written and MapStruct
 * only supplies the Spring component glue.</p>
 */
@Mapper(componentModel = "spring")
public interface RevokedRefreshTokenMapper {

    /**
     * Domain → Entity. Used when recording a revocation.
     */
    default RevokedRefreshTokenEntity toEntity(RevokedRefreshToken token) {
        return RevokedRefreshTokenEntity.builder()
                .jti(token.jti())
                .userId(token.userId().value())
                .revokedAt(token.revokedAt())
                .expiresAt(token.expiresAt())
                .build();
    }

    /**
     * Entity → Domain. Used when loading from the database (mostly for tests
     * and future admin endpoints — the production refresh/logout paths only
     * need the boolean existence check, not the full record).
     */
    default RevokedRefreshToken toDomain(RevokedRefreshTokenEntity entity) {
        return RevokedRefreshToken.reconstitute(
                entity.getJti(),
                UserId.of(entity.getUserId()),
                entity.getRevokedAt(),
                entity.getExpiresAt()
        );
    }
}
