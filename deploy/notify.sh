# Отправка сообщений владельцу в Telegram; подключается из backup.sh и health-check.sh.
# Настройки — в .env.monitor (см. env.monitor.example). Файла нет — оповещения выключены.
# В сообщениях только технические данные, никаких личных.

MONITOR_ENV=.env.monitor
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
TELEGRAM_API=${TELEGRAM_API:-https://api.telegram.org}
[ -f "$MONITOR_ENV" ] && . "./$MONITOR_ENV"

notify_enabled() {
  [ -n "$TELEGRAM_BOT_TOKEN" ] && [ -n "$TELEGRAM_CHAT_ID" ]
}

# Адрес с токеном передаётся через stdin: иначе он был бы виден в списке процессов.
# Код выхода не 0, если Telegram не принял сообщение: тогда вызывающий повторит его позже.
notify() {
  printf 'url = "%s/bot%s/sendMessage"\n' "$TELEGRAM_API" "$TELEGRAM_BOT_TOKEN" |
    curl -fsS -m 15 --retry 3 -o /dev/null -K - \
      --data-urlencode "chat_id=$TELEGRAM_CHAT_ID" \
      --data-urlencode "text=MaxCare: $1"
}

# Сообщить о состоянии только при его смене. $1 — файл состояния, $2 — текст проблемы, пусто — всё
# хорошо, $3 — что именно проверяется (для сообщения о восстановлении).
# Цифры при сравнении не учитываются: иначе «диск 86 %» и «диск 87 %» считались бы новой проблемой.
# Состояние пишется только после успешной отправки, поэтому при сбое Telegram сообщение повторится.
# Пока проблема не решена, раз в 6 часов приходит напоминание.
notify_state() {
  state_file=$1
  problem=$2
  if [ -z "$problem" ]; then
    [ -f "$state_file" ] || return 0
    notify "$3: снова в порядке" && rm -f "$state_file"
    return
  fi
  key=$(printf '%s' "$problem" | tr -d '0-9')
  if [ -f "$state_file" ] && [ "$(cat "$state_file")" = "$key" ] &&
    [ -z "$(find "$state_file" -mmin +360)" ]; then
    return 0
  fi
  notify "$problem" && printf '%s' "$key" > "$state_file"
}
