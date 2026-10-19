-- Ограничение времени в приложениях: правила родителя для одного телефона.
-- Цель правила — приложение (пакет) или группа по категории Android (video, social, game, audio).
-- daily_limit_min — дневной лимит; session_min + break_min — цикл «сессия / перерыв».
-- Хотя бы одно из двух задано; сессия и перерыв задаются только вместе (проверяет API).
create table app_limit_rules (
    device_id       uuid    not null references devices (id) on delete cascade,
    kind            text    not null check (kind in ('app', 'group')),
    target          text    not null,
    daily_limit_min integer,
    session_min     integer,
    break_min       integer,
    primary key (device_id, kind, target)
);

-- Приложения, которые не блокируются никаким правилом (кроме системных — их телефон
-- не блокирует сам: звонки, SMS, MaxCare Kids).
create table app_limit_allowed (
    device_id    uuid not null references devices (id) on delete cascade,
    package_name text not null,
    primary key (device_id, package_name)
);
