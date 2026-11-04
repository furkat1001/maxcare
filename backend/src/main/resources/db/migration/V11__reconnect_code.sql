-- Код «подключить заново»: новый телефон занимает место уже существующего ребёнка,
-- история, маршрут и ограничения сохраняются. null — обычный код для нового ребёнка.
alter table pairing_codes
    add column device_id uuid references devices (id) on delete cascade;
