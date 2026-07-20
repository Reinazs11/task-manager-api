-- Initial schema for the Task Manager API.
--
-- Captures the DDL the application relies on (matches the JPA entities in
-- com.renan.taskmanager.{users,tasks}.infrastructure). Owned by Flyway from
-- here on: changes go in V2__, V3__, etc. — never edit this file after it has
-- been applied to any environment.
--
-- Conventions:
--   * UUIDs stored as PostgreSQL uuid (not as text/varchar). The JPA entities
--     use GenerationType.UUID, which assigns the id in Java and persists it as
--     a real uuid column — cheaper storage and correct indexing.
--   * Timestamps are timestamptz (instants on the UTC timeline). The domain
--     uses java.time.Instant; this type round-trips without a timezone
--     conversion surprise.
--   * owner_id is denormalized on tasks for fast authorization checks
--     (see TaskEntity javadoc).
--   * Foreign keys are ON DELETE CASCADE: deleting a user or project cleans up
--     its tasks/projects automatically. The application enforces ownership
--     first, so a cascade only fires when the owner themselves deletes.

CREATE TABLE users (
    id            uuid         NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    name          varchar(255),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uc_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id         uuid         NOT NULL,
    owner_id   uuid         NOT NULL,
    name       varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id         uuid         NOT NULL,
    project_id uuid         NOT NULL,
    owner_id   uuid         NOT NULL,
    title      varchar(255) NOT NULL,
    status     varchar(255) NOT NULL,
    priority   varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT ck_tasks_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- Index the denormalized owner_id and the project_id on tasks: every
-- "list my tasks" / "list tasks in project" query filters on these columns.
CREATE INDEX idx_tasks_owner   ON tasks (owner_id);
CREATE INDEX idx_tasks_project ON tasks (project_id);

-- Index projects by owner for "list my projects".
CREATE INDEX idx_projects_owner ON projects (owner_id);
