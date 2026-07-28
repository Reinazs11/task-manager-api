package com.renan.taskmanager.common.audit.api;

import com.renan.taskmanager.common.audit.application.ListAuditEventsUseCase;
import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.api.ErrorResponse;
import com.renan.taskmanager.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit trail read endpoint. Requires authentication.
 *
 * <p>The only route here returns the caller's own audit events. There is no
 * admin/global read path — authorization is enforced by scoping the query to
 * {@link AuthenticatedUser#id()} at the use-case layer (DECISIONS.md
 * limitation [4]: no roles beyond ROLE_USER).</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit", description = "Read-only audit trail of the authenticated user's own state-changing operations.")
public class AuditController {

    private final ListAuditEventsUseCase listAuditEventsUseCase;

    public AuditController(ListAuditEventsUseCase listAuditEventsUseCase) {
        this.listAuditEventsUseCase = listAuditEventsUseCase;
    }

    @GetMapping("/events")
    @Operation(summary = "List the authenticated user's audit events",
            description = "Returns a paginated list of audit events caused by the requester "
                    + "(project/task mutations, logins, logouts, refresh rotations), newest first. "
                    + "An optional action filter narrows the result (e.g. only PROJECT_DELETED). "
                    + "Only the caller's own events are ever returned — there is no cross-user access.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of audit events",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuditEventResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AuditEventResponse>> list(
            @Parameter(description = "Optional action filter (e.g. PROJECT_DELETED, USER_LOGIN_SUCCEEDED)")
            @RequestParam(name = "action", required = false) AuditAction action,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditEvent> events = listAuditEventsUseCase.execute(AuthenticatedUser.id(), action, pageable);
        return ResponseEntity.ok(events.map(AuditEventResponse::from));
    }
}
