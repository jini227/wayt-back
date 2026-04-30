alter table user_accounts
    add column default_travel_mode varchar(20);

alter table user_accounts
    add column travel_mode_onboarding_completed boolean not null default false;
