-- Последний выход телефона на связь: если отметки прекратились — приложение удалено,
-- телефон выключен или нет сети.
alter table devices add column last_seen_at timestamptz;

-- Только факты: что и когда. Никакого содержимого экрана, переписки или файлов.
create table device_events (
    device_id   uuid        not null references devices (id) on delete cascade,
    id          uuid        not null, -- создаётся на телефоне: повторная отправка не дублирует
    type        text        not null,
    occurred_at timestamptz not null, -- по часам телефона, ребёнок может их перевести
    received_at timestamptz not null, -- по часам сервера
    primary key (device_id, id)
);

create index device_events_recent_idx on device_events (device_id, received_at desc);
