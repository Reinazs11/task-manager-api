package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port (DDD): read side of the audit store.
 *
 * <p>Lives in the {@code application} layer (not {@code domain}) because its
 * signature uses {@link Page}/{@link Pageable} from Spring Data — the same
 * convention as {@code tasks.application.ports.ProjectQueryPort}. The domain
 * stays free of Spring Data types.</p>
 *
 * <p>The single method always scopes results to a single {@link UserId}: an
 * audit row is only ever returned to its own actor. There is no admin / global
 * read path — {@code DECISIONS.md} limitation [4] (no roles beyond ROLE_USER).</p>
 */
public interface AuditEventQueryPort {

    /**
     * Returns the {@code actorId}-owned audit events, newest first, optionally
     * narrowed to a single {@code action}. Pagination is the caller's
     * responsibility (the controller applies a {@code @PageableDefault}).
     */
    Page<AuditEvent> findByActor(UserId actorId, AuditAction action, Pageable pageable);
}
