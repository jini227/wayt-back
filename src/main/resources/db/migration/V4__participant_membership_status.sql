alter table participants add column membership_status varchar(20) not null default 'ACTIVE';
alter table participants add column left_at timestamp with time zone;
alter table participants add column removed_at timestamp with time zone;
alter table participants add column removed_by_id uuid references user_accounts(id);

create index idx_participants_appointment_membership_status
    on participants(appointment_id, membership_status);
