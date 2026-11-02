#!/bin/sh
# Проверка, что копию можно восстановить: разворачивает её во временной базе и печатает только
# количества строк — без личных данных. Рабочая база не затрагивается, временная без сети и в памяти.
#   ./restore-check.sh <копия.sql.gz.age> <файл с закрытым ключом age>
#   ./restore-check.sh <копия.sql.gz>
# «restore ok» печатается только при полном успехе, при любом сбое — код выхода не 0.
set -eu

BACKUP=$1
KEY=${2:-}
NAME="maxcare-restore-check-$$"
WORK=$(mktemp -d)

cleanup() {
  rm -rf "$WORK"
  docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# В sh нет pipefail: сбой age в конвейере был бы не виден, и пустой ввод выглядел бы как успех.
# Поэтому сначала расшифровка и проверка архива, а база — только потом.
if [ -n "$KEY" ]; then
  age -d -i "$KEY" -o "$WORK/dump.sql.gz" "$BACKUP"
else
  cp "$BACKUP" "$WORK/dump.sql.gz"
fi
gunzip -t "$WORK/dump.sql.gz"

docker run -d --rm --name "$NAME" --network none --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=maxcare -e POSTGRES_DB=maxcare \
  postgres:17-alpine >/dev/null

sql() {
  docker exec "$NAME" psql -tA -v ON_ERROR_STOP=1 -h 127.0.0.1 -U maxcare -d maxcare -c "$1"
}

# Сервер в образе перезапускается после инициализации, поэтому ждём ответа и по TCP.
i=0
until docker exec "$NAME" pg_isready -q -h 127.0.0.1 -U maxcare; do
  i=$((i + 1))
  [ "$i" -gt 60 ] && { echo "временная база не поднялась"; exit 1; }
  sleep 1
done

gunzip -c "$WORK/dump.sql.gz" |
  docker exec -i "$NAME" psql -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U maxcare -d maxcare >/dev/null

for t in families parents devices device_events device_locations; do
  # Присваивание отдельной строкой: так set -e останавливает скрипт, если запрос не прошёл.
  count=$(sql "select count(*) from $t")
  echo "$t $count"
done
migration=$(sql "select max(version::int) from flyway_schema_history where version is not null")
echo "migration $migration"
echo "restore ok"
