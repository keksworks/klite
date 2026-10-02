--changeset db-sessions
create table if not exists db_sessions (
  id varchar(36) primary key,
  data text not null
)
