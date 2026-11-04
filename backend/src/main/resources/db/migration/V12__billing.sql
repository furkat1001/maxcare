-- Подписка семьи из Google Play через RevenueCat. Сервер не верит приложению на слово:
-- paid_until записывается только после запроса к RevenueCat.
alter table families
    add column paid_until        timestamptz,
    add column paid_product      text,
    add column billing_synced_at timestamptz;
