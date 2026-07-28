-- V3: audit_events
--
-- Append-only audit trail for state-changing operations (issue #9).
-- Each row records that some actor performed an action at a point in time.
--
-- Conventions (mirrors V1__init_schema.sql and V2__refresh_token_revocation.sql):
--   * uuid PK assigned by the application (no DEFAULT gen_random_uuid()),
--     matching how every other id is generated in the domain layer.
--   * timestamptz for every timestamp, no DEFAULT (the application supplies
--     the value via the injected Clock — see ClockConfig).
--   * FKs/CHECKs explicitly named (fk_/ck_) so a violation points at the rule.
--   * snake_case column names; the @Entity maps each one with @Column(name=..).
--
-- Atomicity: the application inserts these rows in the SAME transaction as
-- the business operation (DECISIONS.md #21), so a rollback discards the audit
-- row too — a failed action leaves no trail claiming it succeeded. The single
-- exception is USER_LOGIN_FAILED, which is persisted in its own transaction
-- before the InvalidCredentialsException is thrown, so the forensic record of
-- a failed login survives (the most valuable audit signal).
--
-- PII policy: the metadata jsonb is allowlisted by the application to flat
-- non-sensitive fields only (e.g. {from, to} for status changes). It must
-- never carry request bodies, passwords, emails, or free text.
--
-- Retention: the table grows unbounded for now. A retention/purge policy is a
-- documented known limitation; the index on (actor_id, occurred_at) keeps the
-- "my recent activity" query cheap regardless of table size.

CREATE TABLE audit_events (
    id             uuid          NOT NULL,
    actor_id       uuid,             -- nullable: USER_LOGIN_FAILED never records one
    action         varchar(50)   NOT NULL,
    entity_type    varchar(20)   NOT NULL,
    entity_id      uuid,             -- nullable: login/logout/refresh have no entity
    occurred_at    timestamptz   NOT NULL,
    correlation_id varchar(64),      -- nullable: non-HTTP callers have none
    metadata       jsonb         NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_audit_events_actor
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_audit_events_action
        CHECK (action IN ('PROJECT_CREATED',
                          'PROJECT_DELETED',
                          'TASK_CREATED',
                          'TASK_STATUS_CHANGED',
                          'USER_REGISTERED',
                          'USER_LOGIN_SUCCEEDED',
                          'USER_LOGIN_FAILED',
                          'USER_LOGOUT',
                          'REFRESH_ROTATED')),
    CONSTRAINT ck_audit_events_entity_type
        CHECK (entity_type IN ('PROJECT', 'TASK', 'USER'))
);

-- Cover the two access patterns:
--   1. "show me MY recent activity" (AuditController GET /audit/events) —
--      filtered by actor_id, newest first.
--   2. incident review by action ("who deleted a project today?").
CREATE INDEX idx_audit_events_actor ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_events_action ON audit_events (action);
