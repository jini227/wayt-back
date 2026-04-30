alter table appointments add column completed_at timestamp with time zone;
alter table appointments add column completion_reason varchar(30);
create index idx_appointments_completed_at on appointments(completed_at);
