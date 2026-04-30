alter table push_tokens add column environment varchar(20) not null default 'development';
alter table push_tokens add column device_id varchar(120);
alter table push_tokens add column app_version varchar(40);
alter table push_tokens add column last_seen_at timestamp with time zone;
alter table push_tokens add column invalidated_at timestamp with time zone;

update push_tokens set last_seen_at = updated_at where last_seen_at is null;
alter table push_tokens alter column last_seen_at set not null;

create table notification_preferences (
    id uuid primary key,
    user_account_id uuid not null references user_accounts(id),
    notification_type varchar(80) not null,
    enabled boolean not null,
    updated_at timestamp with time zone not null,
    constraint uk_notification_preferences_user_type unique (user_account_id, notification_type)
);

create table notification_jobs (
    id uuid primary key,
    recipient_id uuid not null references user_accounts(id),
    push_token_id uuid not null references push_tokens(id),
    appointment_id uuid references appointments(id),
    notification_type varchar(80) not null,
    event_key varchar(240) not null unique,
    title varchar(120) not null,
    body varchar(500) not null,
    data_json text not null,
    scheduled_at timestamp with time zone not null,
    status varchar(30) not null,
    retry_count integer not null default 0,
    next_attempt_at timestamp with time zone,
    ticket_id varchar(120),
    receipt_status varchar(30),
    error_code varchar(120),
    error_message varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    sent_at timestamp with time zone
);

create index idx_notification_jobs_due on notification_jobs(status, scheduled_at, next_attempt_at);
create index idx_notification_jobs_ticket on notification_jobs(ticket_id);
create index idx_push_tokens_user_active on push_tokens(user_account_id, invalidated_at);
