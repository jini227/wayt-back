create table saved_places (
    id uuid primary key,
    owner_id uuid not null references user_accounts(id),
    label varchar(80) not null,
    place_name varchar(200) not null,
    latitude double precision not null,
    longitude double precision not null,
    favorite boolean not null,
    use_count integer not null,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (owner_id, place_name, latitude, longitude)
);

create index idx_saved_places_owner_favorite_last_used on saved_places(owner_id, favorite, last_used_at);
