-- Время на экране по приложениям за день (по местному времени телефона).
-- Только «какое приложение и сколько минут» — что ребёнок делал внутри, не собирается (CLAUDE.md).
-- Номера V5 и V6 заняты миграциями фильтров в соседней ветке бэкенда.
-- Телефон присылает снимок дня целиком: строки дня заменяются, повтор безопасен.
create table app_usage (
    device_id     uuid        not null references devices (id) on delete cascade,
    day           date        not null, -- календарный день по часам телефона
    package_name  text        not null,
    label         text        not null, -- название приложения, как его видит ребёнок
    foreground_ms bigint      not null, -- сколько приложение было на экране
    updated_at    timestamptz not null, -- по часам сервера: когда пришёл последний снимок
    primary key (device_id, day, package_name)
);
