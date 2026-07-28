package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read-side use case: returns the audit trail scoped to a single actor.
 *
 * <p>Authorization is enforced at this layer: the only input is the caller's
 * own {@link UserId} (obtained from the JWT in the controller), and the
 * underlying {@link AuditEventQueryPort} filters by that id. No actor can ever
 * see another's events — there is no admin/global read path (DECISIONS.md
 * limitation [4]: no roles beyond ROLE_USER).</p>
 */
@Service
public class ListAuditEventsUseCase {

    private final AuditEventQueryPort queryPort;

    public ListAuditEventsUseCase(AuditEventQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    /**
     * @param actorId  the caller's id (authorization scope)
     * @param action   optional filter (e.g. "only my project deletions")
     * @param pageable sort and page (the controller applies a default)
     */
    public Page<AuditEvent> execute(UserId actorId, AuditAction action, Pageable pageable) {
        return queryPort.findByActor(actorId, action, pageable);
    }
}
