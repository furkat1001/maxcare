-- Пробный период семьи: 14 дней с регистрации. Оплаты пока нет — срок только показывается родителю.
-- Срок хранится, а не вычисляется: так его можно продлить конкретной семье, не меняя код.
alter table families add column trial_ends_at timestamptz;

update families set trial_ends_at = created_at + interval '14 days';

alter table families alter column trial_ends_at set not null;
