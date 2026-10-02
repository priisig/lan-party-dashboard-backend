-- Seat orientation is no longer derived from the beamer side. The beamer, entrance and other landmarks
-- become room markers that can be placed on any edge of the seat map.

alter table event add column seat_orientation varchar(10) not null default 'ROWS';
alter table event add column seat_rows_reversed boolean not null default false;
alter table event add column seat_numbers_reversed boolean not null default false;

create table room_marker (
    id        bigserial primary key,
    event_id  bigint not null references event (id) on delete cascade,
    kind      varchar(10) not null default 'OTHER',
    label     varchar(60) not null,
    side      varchar(10) not null,
    align     varchar(10) not null default 'CENTER',
    sort      integer not null default 0
);

-- Keep existing maps looking the same: a side beamer used to rotate rows into columns, and row A
-- was always the row closest to the stage.
update event set seat_orientation = case when beamer_side in ('LEFT', 'RIGHT') then 'COLUMNS' else 'ROWS' end,
                 seat_rows_reversed = beamer_side in ('RIGHT', 'BOTTOM');

insert into room_marker (event_id, kind, label, side, align, sort)
select id, 'BEAMER', 'Bühne · Beamer', beamer_side, 'CENTER', 0 from event;

insert into room_marker (event_id, kind, label, side, align, sort)
select id, 'OTHER', seat_label_start, case when beamer_side in ('LEFT', 'RIGHT') then 'TOP' else 'LEFT' end, 'CENTER', 1
from event where seat_label_start is not null;

insert into room_marker (event_id, kind, label, side, align, sort)
select id, 'OTHER', seat_label_end, case when beamer_side in ('LEFT', 'RIGHT') then 'BOTTOM' else 'RIGHT' end, 'CENTER', 2
from event where seat_label_end is not null;

alter table event drop column beamer_side;
alter table event drop column seat_label_start;
alter table event drop column seat_label_end;
