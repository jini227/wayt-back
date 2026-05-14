alter table user_accounts
    add column if not exists default_travel_mode varchar(20);

alter table user_accounts
    add column if not exists travel_mode_onboarding_completed boolean not null default false;
