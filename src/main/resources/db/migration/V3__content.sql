-- Network / Teamspeak card with WLAN QR, seat self-service rules, coloured headings, announcement kinds.

alter table event add column wifi_ssid varchar(64);
alter table event add column wifi_password varchar(64);
alter table event add column wifi_security varchar(10) not null default 'WPA';
alter table event add column wifi_hidden boolean not null default false;
alter table event add column lan_ip_mode varchar(60);
alter table event add column lan_subnet varchar(60);
alter table event add column lan_gateway varchar(60);
alter table event add column ts_address varchar(120);
alter table event add column ts_port integer;
alter table event add column ts_password varchar(60);

alter table event add column seat_selection_open boolean not null default true;
alter table event add column seat_change_allowed boolean not null default true;
alter table event add column seat_approval_required boolean not null default false;
alter table event add column seat_info varchar(500);

alter table event add column login_headline varchar(300);
-- JSON object: section key -> heading text with optional {farbe:Wort} markup
alter table event add column headings text not null default '{}';

alter table announcement add column kind varchar(10) not null default 'INFO';
