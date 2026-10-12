# Спринты 3–4 — план доводки

Спринты 3 (служба доступности: текст, поиск, словарь, заглушка) и 4 (ИИ-фильтр
изображений по скриншотам, размытие и блок) есть черновиком на ветке
`feature/accessibility-filter` (2 коммита, «team-authored»). В `main` и
`feature/parent-app` не влит. Критерии готовности — в [roadmap.md](roadmap.md),
механизмы и ограничения — в [research.md](research.md).

## Состояние черновика

**Есть:** `TextNormalizer` (+ тест), `ForbiddenWords` (стартовый словарь),
`MyTrackerService` (AccessibilityService: обход дерева, чтение текста, очистка
поля ввода, запуск заглушки), `ScreenshotScanner` (`takeScreenshot`, троттлинг),
`NudeNetClassifier` (LiteRT NSFW), `BlockerActivity` (заглушка, строки `uz`,
`uz-Cyrl`, `ru`, default).

**Спасаем:** `TextNormalizer` (после фикса багов), `BlockerActivity`,
`ScreenshotScanner`, `NudeNetClassifier` (переезжает в `ml`).
**Переписываем:** обвязку службы (Hilt/Coroutines/манифест), хранение словаря,
интеграцию событий.

**Базис:** ветка отстала на 13 коммитов от `feature/parent-app`. Модули не
пересекаются, поэтому отребейзить на актуальный базис (или перенести 2 коммита на
свежую ветку) и работать от него.

## Опора на существующие паттерны

- Межмодульные контракты — интерфейсы в `core` (образец `core/filter/NetworkFilter`).
- DI — Hilt, `@Module`/`@Binds` в `di/` каждого модуля (образец `netfilter/di/NetfilterModule`).
- События — `core/events` `EventRecorder`/`EventType` (`wireName` совпадает с
  `backend/.../EventService.kt`), с rate-limit (образец `netfilter/BlockReporter`).
- У каждого модуля свой `AndroidManifest.xml`; `app-kids` уже зависит от
  `:accessibility`, `:ml`, `:blocker`.
- Асинхронность — Coroutines/Flow, `@ApplicationScope`, не блокировать главный поток.

## PR 0 — интерфейсы в `core` (без логики)

Развязать параллельную работу, задать контракты.

- `core/filter/ContentFilter` — жизненный цикл службы (start/stop вместе с защитой).
- `core/ml/ImageClassifier` — `suspend fun classify(bitmap): Verdict`
  (`Safe`/`Nudity`/`Violence` + score); `accessibility` не зависит от `ml` напрямую.
- Новые `EventType` с `wireName`: `search_blocked`, `text_flagged_messenger`,
  `image_blocked_nudity`, `image_blocked_violence`, `uninstall_attempt_blocked`,
  `accessibility_off`. `wireName` обязан совпасть с бэкендом — отдельная задача
  на `EventService.kt` + отрисовка в parent-app (скоординировать).

Ревью: человек (core). Правок поведения нет, только компиляция.

## PR 1 — объявление и регистрация службы

Делает черновик запускаемым (сейчас блокер: нет манифеста и конфига).

- `accessibility/src/main/AndroidManifest.xml`: `<service>` с
  `BIND_ACCESSIBILITY_SERVICE`, `exported=false`;
  `res/xml/accessibility_service_config.xml` (eventTypes, `canRetrieveWindowContent`,
  `canTakeScreenshot` для API 30+).
- Переименовать `MyTrackerService` → `ContentFilterService`, убрать из логов
  «Tracker/Tracking».
- Hilt в службу через `EntryPointAccessors` (AccessibilityService сам не инжектится).
- `blocker/src/main/AndroidManifest.xml`: объявить `BlockerActivity`.
- Заметное раскрытие (prominent disclosure) перед включением службы — экран в
  `setup` (политика Google Play по Accessibility).

Проверка на телефоне: служба включается, заглушка по тестовому триггеру;
Xiaomi/HyperOS — автозапуск. Ревью: человек.

## PR 2 — текстовый фильтр: нормализация + словарь

Критерий спринта 3 — стоп-слово в Chrome/YouTube очищается < 1 с.

- Фикс связки normalizer↔словарь (сейчас баг): `x→х` и срезание пробелов ломают
  латинские `xxx`/`sex` и матч точных слов через `split`. Выбрать один канонический
  алфавит, словарь хранить уже нормализованным, покрыть тестами каждый случай
  (латиница/кириллица/транслит/leet/повторы).
- Словарь — из кода в шифрованные assets (Tink через `core` SecureStore) или
  remote config, обновляемый без релиза. Стартовый список — синтетический.
- Обход дерева узлов — с главного потока в Coroutines, дебаунс, различать поле
  поиска и произвольный текст.
- События через `EventRecorder` с rate-limit; текст — не в логи/диск; мессенджер →
  только короткий фрагмент-оповещение.

Тесты: normalizer, `ForbiddenWords`, матчер. Проверка: замер < 1 с на телефоне.
Ревью: человек (`accessibility/`).

## PR 3 — перехват удаления (защита в обычном режиме)

Ключевой анти-обход спринта 3 (см. [research.md](research.md), проверка на Samsung
S24 Ultra).

- Ловить экран «Deactivate and uninstall» / карточку приложения в
  `com.android.settings` службой доступности → перекрыть заглушкой + событие
  `uninstall_attempt_blocked` + оповещение родителю.
- OEM-разнобой велик → матрица телефонов.

Проверка: Samsung (есть база) + ещё 2–3 бренда. Ревью: человек.

## PR 4 — модель и инференс в модуль `ml`

Вынести ИИ туда, где ему место, и уложиться в бюджет.

- Перенести `NudeNetClassifier` + классификацию из `ScreenshotScanner` в `ml`;
  отдавать через `core/ml/ImageClassifier`, забиндить Hilt. `accessibility` зависит
  от интерфейса, не от `ml`.
- Управление моделью: загрузка из mapped-файла, обновление с сервера без релиза
  (версия, checksum, Tink). Файл модели в репозиторий не коммитим.
- `ml-research/` (Python): пайплайн обучения/оценки/экспорта для наготы
  (OpenNSFW2/NudeNet — проверить лицензию), экспорт tflite, харнесс метрик. Только
  публичные/синтетические взрослые датасеты, никаких изображений несовершеннолетних.
- Perf: ≤ 100 мс/кадр на среднем телефоне; пропуск при неизменном экране
  (хеш/сигнал изменения); кадр 1–2 с; замер батареи. Зафиксировать пропуски,
  FP/100, батарею — критерий спринта 4.
- Только в памяти, recycle битмапов, ничего в диск/логи/Crashlytics.

Ревью: человек.

## PR 5 — блокировка/размытие + события изображений

- `blocker`: размытие (не только полный блок); вердикт → размытие или блок по
  уровню строгости. Белые списки родителя и разблокировка из отчёта — против
  ложных срабатываний (можно частично здесь, частично позже).
- События `image_blocked_*` с rate-limit, без содержимого.

Ревью: человек.

## PR 6 — насилие (может уйти за MVP)

- Модель насилия (fine-tune MobileNet/EfficientNet) — главный R&D-риск; отдельным
  треком через тот же `ImageClassifier`.

## PR 7 (или в составе серверного синка) — родительская настройка фильтра

Сейчас словарь статический (asset в APK), меняется только пересборкой. Задача —
дать родителю влиять на фильтр без релиза. Архитектурный шов уже есть:
`ForbiddenWords` принимает любой `Collection<String>`, `DictionarySource` —
единственная точка загрузки.

- **Свои стоп-слова родителя**: экран в parent-app (добавить/удалить) → хранение
  по семье на бэкенде → синк на телефон ребёнка → мерж с базовым словарём,
  локально зашифровано (Keystore + Tink), перечитывание `ForbiddenWords` без
  обновления приложения. Родительские слова проходят тот же `TextNormalizer`.
- **Белые списки** (разрешить слово) — против ложных срабатываний.
- **Уровни строгости** (Standard/Strict) — влияют и на текст, и на картинки.

Технически это тот же механизм «обновление с сервера без релиза», что и у моделей
(PR 4), — делать вместе с ним или отдельным PR после него. Три стороны:
parent-app + backend + синк на телефоне.

## Сквозное (закрытие спринтов)

- Backend + parent-app: новые `wireName` в `EventService` и их отрисовка у родителя.
- Google Play: декларации службы доступности (видео + заметное раскрытие), доступа
  к фото/видео, обновить Data safety.
- Зелёное: `lint detekt ktlintCheck`, все тесты.
- Матрица телефонов: Samsung (база), Xiaomi/HyperOS, Tecno/Infinix, Pixel;
  Android 8–16. Повторить чек-лист обхода (отключение службы; «Расширенная защита»
  Android 17 → сообщить родителю).

## Порядок

PR 0 → 1 первыми (без них ничего не запускается). Дальше текстовый трек (2, 3) и
ИИ-трек (4, 5) идут параллельно. 6 — последним/опционально.
