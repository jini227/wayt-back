create table user_accounts (
    id uuid primary key,
    provider varchar(20) not null,
    provider_user_id varchar(120) not null,
    wayt_id varchar(40) not null unique,
    nickname varchar(80) not null,
    avatar_url varchar(500),
    subscription_tier varchar(20) not null,
    default_travel_mode varchar(20),
    travel_mode_onboarding_completed boolean not null default false,
    created_at timestamp with time zone not null
);

create table appointments (
    id uuid primary key,
    host_id uuid not null references user_accounts(id),
    title varchar(120) not null,
    place_name varchar(200) not null,
    latitude double precision not null,
    longitude double precision not null,
    scheduled_at timestamp with time zone not null,
    share_start_offset_minutes integer not null,
    penalty varchar(200) not null,
    arrival_radius_meters integer not null,
    grace_minutes integer not null,
    memo text,
    created_at timestamp with time zone not null
);

create table participants (
    id uuid primary key,
    appointment_id uuid not null references appointments(id),
    user_account_id uuid not null references user_accounts(id),
    role varchar(20) not null,
    status varchar(40) not null,
    travel_mode varchar(20) not null,
    location_consent boolean not null,
    joined_at timestamp with time zone not null,
    eta_calculated_at timestamp with time zone,
    eta_next_eligible_at timestamp with time zone,
    eta_refresh_policy varchar(40) not null,
    eta_api_call_count integer not null,
    manual_estimated_arrival_at timestamp with time zone,
    manual_eta_updated_at timestamp with time zone,
    unique (appointment_id, user_account_id)
);

create table invites (
    id uuid primary key,
    appointment_id uuid not null references appointments(id),
    inviter_id uuid not null references user_accounts(id),
    invitee_id uuid references user_accounts(id),
    type varchar(20) not null,
    status varchar(20) not null,
    token varchar(80) not null unique,
    target_wayt_id varchar(40),
    created_at timestamp with time zone not null
);

create table location_samples (
    id uuid primary key,
    appointment_id uuid not null references appointments(id),
    participant_id uuid not null references participants(id),
    latitude double precision not null,
    longitude double precision not null,
    accuracy_meters double precision not null,
    captured_at timestamp with time zone not null
);

create table arrival_records (
    id uuid primary key,
    appointment_id uuid not null references appointments(id),
    participant_id uuid not null references participants(id),
    source varchar(20) not null,
    arrived_at timestamp with time zone not null,
    punctuality varchar(20) not null,
    late_minutes bigint not null,
    unique (participant_id)
);

create table status_logs (
    id uuid primary key,
    appointment_id uuid not null references appointments(id),
    participant_id uuid not null references participants(id),
    message varchar(80) not null,
    created_at timestamp with time zone not null
);

create table address_book_entries (
    id uuid primary key,
    owner_id uuid not null references user_accounts(id),
    saved_user_id uuid not null references user_accounts(id),
    display_name varchar(80) not null,
    created_at timestamp with time zone not null,
    unique (owner_id, saved_user_id)
);

create table push_tokens (
    id uuid primary key,
    user_account_id uuid not null references user_accounts(id),
    token varchar(200) not null unique,
    platform varchar(20) not null,
    updated_at timestamp with time zone not null
);

create index idx_appointments_scheduled_at on appointments(scheduled_at);
create index idx_participants_appointment_id on participants(appointment_id);
create index idx_status_logs_appointment_id_created_at on status_logs(appointment_id, created_at);
create index idx_location_samples_appointment_id_captured_at on location_samples(appointment_id, captured_at);
