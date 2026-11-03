#!/bin/sh
# Проверка сервера раз в 5 минут. Запускается cron'ом из папки deploy:
#   */5 * * * * cd /opt/maxcare/deploy && ./health-check.sh >> /var/log/maxcare-health.log 2>&1
# Пишет в Telegram при смене состояния (см. notify.sh). Если сервер пропал совсем, сообщит
# проверка снаружи: .github/workflows/uptime.yml.
set -u

DISK_LIMIT=${DISK_LIMIT:-85}
MEMORY_MIN_MB=${MEMORY_MIN_MB:-150}
CERT_MIN_DAYS=${CERT_MIN_DAYS:-14}
BACKUP_MAX_HOURS=${BACKUP_MAX_HOURS:-26}
RETRY_DELAY=${RETRY_DELAY:-60}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/maxcare}
STATE=${HEALTH_STATE:-/var/tmp/maxcare-health.state}

. ./notify.sh
notify_enabled || exit 0
DOMAIN=$(sed -n 's/^API_DOMAIN=//p' .env)

check() {
  problems=
  running=$(docker compose ps --status running --services 2>/dev/null)
  for service in caddy server postgres redis; do
    printf '%s\n' "$running" | grep -qx "$service" || problems="$problems контейнер $service не работает;"
  done

  # Снаружи: заодно проверяются Caddy, сертификат и DNS.
  curl -fsS -m 20 -o /dev/null "https://$DOMAIN/health" 2>/dev/null ||
    problems="$problems https://$DOMAIN/health не отвечает;"

  disk=$(df --output=pcent / | tail -n 1 | tr -dc '0-9')
  [ "$disk" -lt "$DISK_LIMIT" ] || problems="$problems диск занят на $disk%;"

  memory=$(awk '/MemAvailable/ {print int($2 / 1024)}' /proc/meminfo)
  [ "$memory" -ge "$MEMORY_MIN_MB" ] || problems="$problems доступно памяти $memory МБ;"

  # Caddy продлевает сертификат заранее; если срок всё же подходит, продление сломалось.
  echo | openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null |
    openssl x509 -noout -checkend $((CERT_MIN_DAYS * 86400)) >/dev/null 2>&1 ||
    problems="$problems сертификат истекает менее чем через $CERT_MIN_DAYS дней;"

  # Если ночная копия не запускалась вовсе, backup.sh промолчит: заметить это может только здесь.
  [ -n "$(find "$BACKUP_DIR" -name 'maxcare-*.sql.gz' -mmin -$((BACKUP_MAX_HOURS * 60)) 2>/dev/null)" ] ||
    problems="$problems нет свежей копии базы (старше $BACKUP_MAX_HOURS ч);"
}

check
# Короткий сбой сети или перезапуск контейнера не должны будить ночью.
if [ -n "$problems" ]; then
  sleep "$RETRY_DELAY"
  check
fi

[ -z "$problems" ] || echo "$(date -u +%FT%TZ) HEALTH FAILED:$problems"
message=
[ -z "$problems" ] || message="проблема на сервере:$problems"
notify_state "$STATE" "$message" "сервер" || echo "$(date -u +%FT%TZ) не удалось отправить сообщение"
