-- LAN Dashboard schema. Everything except admins and app settings belongs to an event (one LAN edition).

create table event (
    id                  bigserial primary key,
    slug                varchar(80)  not null unique,
    title               varchar(120) not null,
    subtitle            varchar(200),
    location            varchar(200),
    timezone            varchar(60)  not null default 'Europe/Zurich',
    starts_at           timestamptz  not null,
    ends_at             timestamptz  not null,
    active              boolean      not null default false,
    welcome_title       varchar(300),
    welcome_text        text,
    logo                bytea,
    logo_content_type   varchar(100),
    beamer_side         varchar(10)  not null default 'LEFT',
    seat_label_start    varchar(60),
    seat_label_end      varchar(60),
    kiosk_interval_sec  integer      not null default 30,
    kiosk_views         varchar(200) not null default 'overview,tournaments,seating,stats',
    created_at          timestamptz  not null default now()
);
create unique index event_single_active on event (active) where active;

create table info_item (
    id        bigserial primary key,
    event_id  bigint not null references event (id) on delete cascade,
    label     varchar(100) not null,
    value     varchar(500) not null,
    sort      integer not null default 0
);

create table announcement (
    id         bigserial primary key,
    event_id   bigint not null references event (id) on delete cascade,
    text       varchar(500) not null,
    enabled    boolean not null default true,
    starts_at  timestamptz,
    ends_at    timestamptz,
    sort       integer not null default 0
);

create table game_server (
    id              bigserial primary key,
    event_id        bigint not null references event (id) on delete cascade,
    name            varchar(120) not null,
    short_code      varchar(30),
    host            varchar(200) not null,
    port            integer,
    query_port      integer,
    query_type      varchar(20) not null default 'NONE',
    connect_url     varchar(300),
    visible         boolean not null default true,
    available_from  timestamptz,
    sort            integer not null default 0
);

create table tournament (
    id                      bigserial primary key,
    event_id                bigint not null references event (id) on delete cascade,
    name                    varchar(120) not null,
    color                   varchar(20) not null default '#9B5CFF',
    format_label            varchar(200),
    max_participants        integer not null default 16,
    team_size               integer not null default 1,
    registration_open       boolean not null default false,
    registration_closes_at  timestamptz,
    starts_at               timestamptz,
    server_id               bigint references game_server (id) on delete set null,
    challonge_slug          varchar(200),
    challonge_snapshot      text,
    snapshot_at             timestamptz,
    rules_url               varchar(300),
    sort                    integer not null default 0
);

create table registration (
    id                        bigserial primary key,
    tournament_id             bigint not null references tournament (id) on delete cascade,
    gamertag                  varchar(60) not null,
    team_name                 varchar(80),
    teammates                 varchar(300),
    seat_label                varchar(20),
    challonge_participant_id  bigint,
    created_at                timestamptz not null default now()
);
create unique index registration_unique_name on registration (tournament_id, lower(coalesce(team_name, gamertag)));

create table schedule_item (
    id             bigserial primary key,
    event_id       bigint not null references event (id) on delete cascade,
    starts_at      timestamptz not null,
    ends_at        timestamptz,
    title          varchar(200) not null,
    location       varchar(100),
    color          varchar(20) not null default '#6B6390',
    tournament_id  bigint references tournament (id) on delete set null
);

create table seat_row (
    id          bigserial primary key,
    event_id    bigint not null references event (id) on delete cascade,
    label       varchar(10) not null,
    seat_count  integer not null,
    sort        integer not null default 0
);

create table seat (
    id        bigserial primary key,
    event_id  bigint not null references event (id) on delete cascade,
    row_id    bigint not null references seat_row (id) on delete cascade,
    number    integer not null,
    label     varchar(20) not null,
    status    varchar(10) not null default 'FREE',
    gamertag  varchar(60),
    note      varchar(300),
    unique (event_id, label)
);

create table seat_request (
    id          bigserial primary key,
    seat_id     bigint not null references seat (id) on delete cascade,
    gamertag    varchar(60) not null,
    companions  varchar(300),
    status      varchar(10) not null default 'PENDING',
    created_at  timestamptz not null default now()
);

create table integration (
    id           bigserial primary key,
    event_id     bigint not null references event (id) on delete cascade,
    type         varchar(40) not null,
    name         varchar(120) not null,
    enabled      boolean not null default true,
    sort         integer not null default 0,
    config       text not null default '{}',
    last_result  text,
    last_error   varchar(500),
    last_ok_at   timestamptz
);

create table metric (
    id          bigserial primary key,
    event_id    bigint not null references event (id) on delete cascade,
    key         varchar(60) not null,
    label       varchar(100) not null,
    value       varchar(60) not null,
    unit        varchar(30),
    sort        integer not null default 0,
    updated_at  timestamptz not null default now(),
    unique (event_id, key)
);

create table admin (
    id          bigserial primary key,
    name        varchar(60) not null unique,
    code_hash   varchar(100) not null,
    code_hint   varchar(4) not null,
    enabled     boolean not null default true,
    created_at  timestamptz not null default now()
);

create table app_setting (
    key    varchar(60) primary key,
    value  text
);
