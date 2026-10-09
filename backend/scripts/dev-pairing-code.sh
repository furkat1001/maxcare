#!/usr/bin/env bash
# Только для разработки: выдаёт код привязки, пока нет экрана в приложении родителя.
# Регистрирует синтетического родителя (если его ещё нет), входит и запрашивает код.
#
#   ./scripts/dev-pairing-code.sh [адрес сервера]
#
# Адрес по умолчанию — локальный сервер из `./gradlew run`.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
EMAIL="dev-parent@example.test"
PASSWORD="dev-password-123"
CREDENTIALS="{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"

json_field() {
  sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

curl -s -o /dev/null -X POST "$BASE_URL/v1/parents" \
  -H 'Content-Type: application/json' -d "$CREDENTIALS" || true

TOKEN=$(curl -sf -X POST "$BASE_URL/v1/sessions" \
  -H 'Content-Type: application/json' -d "$CREDENTIALS" | json_field token)

if [ -z "$TOKEN" ]; then
  echo "Не удалось войти. Сервер запущен? ($BASE_URL)" >&2
  exit 1
fi

RESPONSE=$(curl -sf -X POST "$BASE_URL/v1/pairing-codes" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"childName":"Test child"}')

echo "Код привязки:  $(echo "$RESPONSE" | json_field code)"
echo "Действует до:  $(echo "$RESPONSE" | json_field expiresAt)"
