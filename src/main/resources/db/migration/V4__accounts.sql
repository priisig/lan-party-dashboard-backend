-- Participant accounts replace the anonymous forms and the admin codes. Organisers are accounts with role ORGA.

create table app_user (
    id               bigserial primary key,
    nickname         varchar(40)  not null,
    email            varchar(200) not null,
    password_hash    varchar(100) not null,
    role             varchar(10)  not null default 'USER',
    enabled          boolean      not null default true,
    first_name       varchar(60),
    last_name        varchar(60),
    steam            varchar(60),
    discord          varchar(60),
    team             varchar(80),
    favourite_game   varchar(80),
    show_on_seatmap  boolean      not null default true,
    created_at       timestamptz  not null default now()
);
create unique index app_user_nickname on app_user (lower(nickname));
create unique index app_user_email on app_user (lower(email));

-- Per-event state of a participant (payment, check-in); created on the first seat or tournament action.
create table event_participant (
    id          bigserial primary key,
    event_id    bigint not null references event (id) on delete cascade,
    user_id     bigint not null references app_user (id) on delete cascade,
    paid        boolean not null default false,
    checked_in  boolean not null default false,
    created_at  timestamptz not null default now(),
    unique (event_id, user_id)
);

alter table seat add column user_id bigint references app_user (id) on delete set null;
alter table seat_request add column user_id bigint references app_user (id) on delete cascade;
alter table registration add column user_id bigint references app_user (id) on delete set null;
create unique index registration_unique_user on registration (tournament_id, user_id) where user_id is not null;

-- Admin codes are gone; the first organiser is created from LAN_BOOTSTRAP_ADMIN_* on start.
drop table admin;
