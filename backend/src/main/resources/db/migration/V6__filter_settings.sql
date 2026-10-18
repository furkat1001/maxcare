-- Разрешённые слова (белый список) живут в той же таблице и отличаются kind.
-- allow-слово отключает соответствующий корень из словаря фильтра на телефоне.
alter table filter_words
    add column kind text not null default 'block' check (kind in ('block', 'allow'));

-- Уровень строгости фильтра на семью: влияет на порог анализа картинок.
create table family_settings (
    family_id  uuid primary key references families (id) on delete cascade,
    strictness text        not null default 'standard' check (strictness in ('standard', 'strict')),
    updated_at timestamptz not null default now()
);
