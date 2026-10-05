--changeset db_sessions
create table db_sessions(
  id uuid not null primary key,
  params jsonb not null,
  updatedAt timestamptz not null default current_timestamp
);

--changeset db_sessions.updatedAt_idx
create index db_sessions_updatedat_idx on db_sessions (updatedAt);

--changeset db_sessions.grant_delete
-- clear() deletes the session row, so an app user that is otherwise only allowed to select/insert/update needs this
grant delete on db_sessions to app;
