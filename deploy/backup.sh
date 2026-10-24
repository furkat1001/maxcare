#!/bin/sh
# Ежедневная копия базы MaxCare. Запускается cron'ом на сервере из папки deploy:
#   0 3 * * * cd /opt/maxcare/deploy && ./backup.sh >> /var/log/maxcare-backup.log 2>&1
# Хранит последние 14 копий в /var/backups/maxcare (только root и deploy).
# TODO: копировать архивы в другое место (другой провайдер), иначе при потере сервера пропадут и они.
set -eu

DIR=/var/backups/maxcare
KEEP_DAYS=14
FILE="$DIR/maxcare-$(date -u +%Y-%m-%dT%H%M).sql.gz"

mkdir -p "$DIR"
chmod 700 "$DIR"
docker compose exec -T postgres pg_dump -U maxcare --no-owner maxcare | gzip > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
chmod 600 "$FILE"
find "$DIR" -name 'maxcare-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date -u +%FT%TZ) backup ok: $FILE ($(du -h "$FILE" | cut -f1))"
