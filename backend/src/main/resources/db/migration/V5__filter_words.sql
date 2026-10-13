-- Свои стоп-слова родителя: добавляются к базовому словарю фильтра на телефоне ребёнка.
-- Слово хранится нормализованным (trim + нижний регистр); телефон применяет свою нормализацию.
create table filter_words (
    family_id  uuid        not null references families (id) on delete cascade,
    word       text        not null,
    created_at timestamptz not null default now(),
    primary key (family_id, word)
);
