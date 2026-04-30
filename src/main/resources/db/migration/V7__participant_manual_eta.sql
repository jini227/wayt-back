alter table participants add column if not exists manual_estimated_arrival_at timestamp with time zone;
alter table participants add column if not exists manual_eta_updated_at timestamp with time zone;
