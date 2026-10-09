-- Семья — единица привязки: родители и устройства детей. Второй родитель добавится позже.
create table families (
    id         uuid primary key,
    created_at timestamptz not null default now()
);

create table parents (
    id            uuid primary key,
    family_id     uuid        not null references families (id) on delete cascade,
    email         text        not null unique, -- хранится в нижнем регистре
    password_hash text        not null,        -- Argon2id в формате PHC
    created_at    timestamptz not null default now()
);

-- Хранится только SHA-256 токена: утечка базы не даёт войти от имени родителя.
create table parent_sessions (
    token_hash bytea primary key,
    parent_id  uuid        not null references parents (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

-- Одноразовые коды привязки: 8 цифр, живут 10 минут.
create table pairing_codes (
    code       text primary key,
    family_id  uuid        not null references families (id) on delete cascade,
    child_name text,
    expires_at timestamptz not null,
    used_at    timestamptz
);

create table devices (
    id                   uuid primary key,
    family_id            uuid        not null references families (id) on delete cascade,
    child_name           text,
    name                 text        not null,
    platform             text        not null check (platform in ('android', 'ios')),
    token_hash           bytea       not null unique, -- SHA-256 токена устройства
    recovery_secret_enc  bytea       not null,        -- AES-GCM, ключ из MAXCARE_SECRET_KEY
    paired_at            timestamptz not null default now()
);

create index devices_family_idx on devices (family_id);
create index parents_family_idx on parents (family_id);
