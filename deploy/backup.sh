#!/bin/sh
# Ежедневная копия базы MaxCare. Запускается cron'ом на сервере из папки deploy:
#   0 3 * * * cd /opt/maxcare/deploy && ./backup.sh >> /var/log/maxcare-backup.log 2>&1
# Хранит последние 14 копий в /var/backups/maxcare (только root и deploy).
# Если есть файл .env.backup (см. env.backup.example) — ещё и шифрует копию и отправляет в другое
# место: при потере сервера иначе пропали бы и копии. Наружу уходит только зашифрованный файл.
# Закрытый ключ на сервере не хранится: взлом сервера не откроет копии. Порядок — docs/deploy-vps.md.
set -eu

DIR=/var/backups/maxcare
KEEP_DAYS=14
OFFSITE_ENV=.env.backup
FILE="$DIR/maxcare-$(date -u +%Y-%m-%dT%H%M).sql.gz"

mkdir -p "$DIR"
chmod 700 "$DIR"
docker compose exec -T postgres pg_dump -U maxcare --no-owner maxcare | gzip > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
chmod 600 "$FILE"
find "$DIR" -name 'maxcare-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date -u +%FT%TZ) backup ok: $FILE ($(du -h "$FILE" | cut -f1))"

[ -f "$OFFSITE_ENV" ] || exit 0

# shellcheck disable=SC1090
. "./$OFFSITE_ENV"
ENCRYPTED="$FILE.age"
trap 'rm -f "$ENCRYPTED"' EXIT
age -r "$BACKUP_AGE_RECIPIENT" -o "$ENCRYPTED" "$FILE"

# Настройки remote — через окружение: ключ не попадает в аргументы команды и в файл конфигурации.
# --no-check-dest: ключу запрещено читать бакет, а имя файла всё равно каждый раз новое.
export RCLONE_CONFIG_OFFSITE_TYPE=b2
export RCLONE_CONFIG_OFFSITE_ACCOUNT="$BACKUP_B2_KEY_ID"
export RCLONE_CONFIG_OFFSITE_KEY="$BACKUP_B2_APP_KEY"
if rclone copyto --log-level ERROR --no-check-dest --retries 5 --low-level-retries 10 --contimeout 30s --timeout 120s \
    "$ENCRYPTED" "offsite:$BACKUP_B2_BUCKET/$(basename "$ENCRYPTED")"; then
  echo "$(date -u +%FT%TZ) offsite ok: $(basename "$ENCRYPTED") ($(du -h "$ENCRYPTED" | cut -f1))"
else
  # Локальная копия уже есть, но о сбое должно быть видно: код выхода и строка в журнале.
  echo "$(date -u +%FT%TZ) OFFSITE FAILED: $(basename "$ENCRYPTED")"
  exit 1
fi
