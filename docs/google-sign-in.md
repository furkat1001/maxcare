# Вход через Google — настройка

Приложение родителя получает от Google ID-токен и отправляет его на сервер. Сервер проверяет
токен сам (`POST /v1/sessions/google`, см. `docs/backend-api.md`). `google-services.json` не нужен.

## 1. Google Cloud Console

Проект MaxCare → **Google Auth Platform** (раньше «OAuth consent screen»).

1. **Branding:** название «MaxCare», e-mail поддержки, логотип, ссылки на политику
   конфиденциальности и условия.
2. **Audience:** External. Пока приложение в режиме «Testing», войти могут только
   пользователи из списка **Test users**: добавьте туда свои тестовые аккаунты Google.
3. **Data access:** только `openid`, `email`, `profile`. Это базовые scope, проверка Google для них
   не нужна.

**Clients → Create client** — нужны два клиента:

| Тип | Что указать | Зачем |
| --- | --- | --- |
| **Web application** | Название «MaxCare server», остальное пусто | Его ID — получатель ID-токена (`serverClientId`), сервер проверяет именно его. Секрет клиента нам не нужен, не сохраняйте его |
| **Android** | Пакет `uz.maxcare.parent`, SHA-1 сертификата подписи | Google проверяет, что запрос идёт из нашего приложения |

SHA-1 для debug-сборки (ключ разработчика на этом компьютере, не ключ подписи релиза):

```bash
keytool -list -v -alias androiddebugkey -storepass android -keypass android \
  -keystore "%USERPROFILE%\.android\debug.keystore"
```

Для релиза нужен отдельный Android-клиент с SHA-1 ключа подписи; при Play App Signing — с SHA-1
из Play Console («App signing key certificate»).

ID клиентов — не секрет (они видны в приложении), но в репозиторий их не кладём: передаём
при сборке и запуске.

## 2. Сервер

```bash
MAXCARE_GOOGLE_CLIENT_IDS=<ID веб-клиента>.apps.googleusercontent.com ./gradlew run
```

Можно перечислить несколько ID через запятую, например когда появится iOS-клиент.
Пустое значение выключает вход через Google: любой токен получает `401`.

## 3. Приложение родителя

```bash
flutter build apk --debug \
  --dart-define=MAXCARE_API=http://localhost:8080 \
  --dart-define=MAXCARE_GOOGLE_SERVER_CLIENT_ID=<ID веб-клиента>.apps.googleusercontent.com
```

Без `MAXCARE_GOOGLE_SERVER_CLIENT_ID` кнопка «Войти через Google» не показывается.

## Если не работает

- Сразу после выбора аккаунта ничего не происходит. Обычно это неверный SHA-1 или пакет в
  Android-клиенте: Google в этом случае сообщает об отмене, а не об ошибке.
- «Не удалось войти через Google»: на телефоне нет сервисов Google или не указан
  `MAXCARE_GOOGLE_SERVER_CLIENT_ID`.
- Ответ сервера `401`: ID в `MAXCARE_GOOGLE_CLIENT_IDS` не совпадает с ID веб-клиента в приложении.

## iOS (позже)

Нужен iOS-клиент (bundle ID `uz.maxcare.parent`), его ID в `Info.plist` (`GIDClientID` и URL-схема),
и его же добавить в `MAXCARE_GOOGLE_CLIENT_IDS`. Вместе с Google на iOS обязателен вход через Apple
(App Store, правило 4.8).
