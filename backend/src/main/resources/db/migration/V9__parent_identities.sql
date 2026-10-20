-- Вход через Google (позже — Apple). Родитель, вошедший так, может не иметь пароля.
alter table parents alter column password_hash drop not null;

-- subject — постоянный ID пользователя у провайдера (claim `sub`), а не e-mail: адрес может смениться.
create table parent_identities (
    provider   text        not null check (provider in ('google', 'apple')),
    subject    text        not null,
    parent_id  uuid        not null references parents (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (provider, subject)
);

create index parent_identities_parent_idx on parent_identities (parent_id);
