-- Геопозиции телефона ребёнка. Это тоже событие «где и когда» — координаты, не содержимое.
-- id создаётся на телефоне: повторная отправка той же отметки не дублирует строку.
create table device_locations (
    device_id   uuid             not null references devices (id) on delete cascade,
    id          uuid             not null,
    latitude    double precision not null,
    longitude   double precision not null,
    accuracy_m  double precision,          -- радиус погрешности в метрах, если телефон его знает
    recorded_at timestamptz      not null, -- по часам телефона
    received_at timestamptz      not null, -- по часам сервера
    primary key (device_id, id)
);

-- Родитель запрашивает последнюю позицию: берём самую свежую по времени прихода на сервер.
create index device_locations_recent_idx on device_locations (device_id, received_at desc);
